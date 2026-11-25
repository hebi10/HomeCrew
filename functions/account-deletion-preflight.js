import { createHash } from 'node:crypto';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { getFirestore } from 'firebase-admin/firestore';
import { validMemberUid } from './membership-core.js';

const options = { region: 'asia-northeast3', timeoutSeconds: 30, maxInstances: 5 };
const MAX_HOMES = 50;
const has = snapshot => !snapshot.empty;

// Inventory only. Never use the absence of a currently indexed membership
// as authorization to delete Auth or erase other members' shared history.
export const getAccountDeletionPreflight = onCall(options, async request => {
  const uid = request.auth?.uid;
  if (!uid || !validMemberUid(uid)) throw new HttpsError('unauthenticated', 'Firebase account required.');
  const db = getFirestore();
  const homes = db.collection('households');
  const user = db.collection('users').doc(uid);
  const rateKey = createHash('sha256').update(uid).digest('hex');
  return db.runTransaction(async tx => {
    const [owned, joined, indexed, profiles, stages, createdInvites, acceptedInvites,
      createdTasks, assignedTasks, scheduledTasks, assignedRows, completedRows, operations,
      userDoc, ...rates] = await Promise.all([
      tx.get(homes.where('ownerUid', '==', uid).limit(MAX_HOMES + 1)),
      tx.get(db.collectionGroup('members').where('uid', '==', uid).limit(MAX_HOMES + 1)),
      tx.get(user.collection('households').limit(MAX_HOMES + 1)),
      tx.get(db.collectionGroup('memberProfiles').where('uid', '==', uid).limit(1)),
      tx.get(db.collectionGroup('migrationStaging').where('ownerUid', '==', uid).limit(1)),
      tx.get(db.collection('householdInvites').where('createdBy', '==', uid).limit(1)),
      tx.get(db.collection('householdInvites').where('acceptedBy', '==', uid).limit(1)),
      tx.get(db.collectionGroup('chores').where('creatorUid', '==', uid).limit(1)),
      tx.get(db.collectionGroup('chores').where('assigneeUid', '==', uid).limit(1)),
      tx.get(db.collectionGroup('chores').where('schedule.memberUids', 'array-contains', uid).limit(1)),
      tx.get(db.collectionGroup('occurrences').where('assignedUid', '==', uid).limit(1)),
      tx.get(db.collectionGroup('occurrences').where('completedByUid', '==', uid).limit(1)),
      tx.get(db.collectionGroup('operations').where('actorUid', '==', uid).limit(1)),
      tx.get(user),
      ...['create','accept','house-create'].map(action => tx.get(db.collection('inviteRateLimits').doc(action + '-' + rateKey))),
    ]);
    if ([owned, joined, indexed].some(snapshot => snapshot.size > MAX_HOMES)) {
      throw new HttpsError('resource-exhausted', 'Account needs manual inventory review.');
    }
    const ownedIds = new Set(owned.docs.map(doc => doc.id));
    const memberIds = new Set();
    for (const member of joined.docs) {
      const home = member.ref.parent.parent;
      if (!home || home.parent.path !== 'households' || member.id !== uid ||
          member.data().role !== 'member' || member.data().householdId !== home.id ||
          ownedIds.has(home.id)) {
        throw new HttpsError('failed-precondition', 'Membership records need repair.');
      }
      memberIds.add(home.id);
    }
    const ids = [...new Set([...ownedIds, ...memberIds])];
    if (ids.length > MAX_HOMES) throw new HttpsError('resource-exhausted', 'Account needs manual inventory review.');
    const documents = ids.length ? await tx.getAll(...ids.map(id => homes.doc(id))) : [];
    const indexById = new Map(indexed.docs.map(doc => [doc.id, doc.data()]));
    const spaces = [];
    let indexIssues = 0;
    let lockedSpaces = 0;
    for (const home of documents) {
      const role = ownedIds.has(home.id) ? 'OWNER' : 'MEMBER';
      if (!home.exists || (role === 'OWNER' ? home.data().ownerUid !== uid : home.data().ownerUid === uid) ||
          typeof home.data().name !== 'string') {
        throw new HttpsError('failed-precondition', 'Household records need repair.');
      }
      const entry = indexById.get(home.id);
      if (entry?.householdId !== home.id || entry?.role !== role.toLowerCase()) indexIssues++;
      indexById.delete(home.id);
      if (home.data().migrationPublishLock != null) lockedSpaces++;
      spaces.push({ householdId: home.id, name: home.data().name, role });
    }
    indexIssues += indexById.size; // stale index: do not reveal that household's name
    return {
      spaces: spaces.sort((a,b) => a.householdId < b.householdId ? -1 : 1),
      ownerCount: ownedIds.size, memberCount: memberIds.size, indexIssues, lockedSpaces,
      hasProfiles: has(profiles), hasMigrationStage: has(stages),
      hasInvites: has(createdInvites) || has(acceptedInvites),
      hasTaskReferences: has(createdTasks) || has(assignedTasks) || has(scheduledTasks),
      hasOccurrenceReferences: has(assignedRows) || has(completedRows),
      hasOperationReferences: has(operations),
      hasAccountMetadata: userDoc.exists || rates.some(doc => doc.exists),
      // Nested operation payloads, private chunks and retained backups may
      // contain references not represented by these bounded lookups.
      requiresCleanupReview: true,
      deletionAllowed: false,
    };
  }, { readOnly: true });
});
