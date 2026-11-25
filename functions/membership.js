import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { requireHouseholdId, requireRemovableMemberCount } from './invite-core.js';
import { validMemberUid, normalizeMemberDisplayName, ownershipRevision,
  memberRosterDigest, assertDistinctMemberName } from './membership-core.js';

const options = { region: 'asia-northeast3', timeoutSeconds: 30, maxInstances: 10 };

function context(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Firebase account required.');
  let id;
  try { id = requireHouseholdId(request.data?.householdId); }
  catch { throw new HttpsError('invalid-argument', 'Invalid household.'); }
  const db = getFirestore();
  return { db, uid, home: db.collection('households').doc(id) };
}
function joinedIdentity(snapshot) {
  const joined = snapshot.data().joinedAt;
  if (!joined || !Number.isInteger(joined.seconds) || !Number.isInteger(joined.nanoseconds)) {
    throw new HttpsError('failed-precondition', 'Invalid member record.');
  }
  return joined.seconds + ':' + joined.nanoseconds;
}

async function loadRoster(tx, home, uid) {
  const house = await tx.get(home);
  if (!house.exists) throw new HttpsError('not-found', 'Household not found.');
  const data = house.data();
  const ownerUid = data.ownerUid;
  const owner = ownerUid === uid;
  if (!owner) {
    const actor = await tx.get(home.collection('members').doc(uid));
    if (!actor.exists || actor.data().role !== 'member' || actor.data().uid !== uid) {
      throw new HttpsError('permission-denied', 'Household membership required.');
    }
  }
  const [members, staging] = await Promise.all([
    tx.get(home.collection('members').limit(8)),
    tx.get(home.collection('migrationStaging').doc('current')),
  ]);
  if (!validMemberUid(ownerUid) || members.size > 7 ||
      members.size + 1 !== data.memberCount || members.docs.some(doc =>
        doc.id === ownerUid || !validMemberUid(doc.id) || doc.data().uid !== doc.id ||
        doc.data().householdId !== home.id || doc.data().role !== 'member')) {
    throw new HttpsError('failed-precondition', 'Invalid household membership state.');
  }
  const revision = ownershipRevision(data);
  const people = [{ uid: ownerUid, isOwner: true, joinedIdentity: 'owner:' + revision },
    ...members.docs.map(doc => ({ uid: doc.id, isOwner: false, joinedIdentity: joinedIdentity(doc) }))];
  const profiles = await tx.getAll(...people.map(person => home.collection('memberProfiles').doc(person.uid)));
  for (let i = 0; i < people.length; i++) {
    const profile = profiles[i];
    if (profile.exists) {
      const stored = profile.data();
      if (stored.uid !== people[i].uid || !Number.isSafeInteger(stored.revision) ||
          stored.revision < 1 || normalizeMemberDisplayName(stored.displayName) !== stored.displayName) {
        throw new HttpsError('failed-precondition', 'Invalid member profile.');
      }
      Object.assign(people[i], { displayName: stored.displayName, profileRevision: stored.revision });
    } else Object.assign(people[i], { displayName: null, profileRevision: 0 });
  }
  const transferBlocked = staging.exists || data.migrationPublishLock != null;
  const digest = memberRosterDigest(home.id, ownerUid, revision, people, transferBlocked);
  return { data, owner, ownerUid, revision, people, digest, transferBlocked };
}

function checkPreview(request, roster) {
  if (request.data?.expectedRosterDigest !== roster.digest) {
    throw new HttpsError('aborted', 'Membership changed. Refresh before confirming.');
  }
}

export const getHouseholdRoster = onCall(options, async request => {
  const { db, uid, home } = context(request);
  return db.runTransaction(async tx => {
    const roster = await loadRoster(tx, home, uid);
    return {
      householdId: home.id, rosterDigest: roster.digest, transferBlocked: roster.transferBlocked,
      members: roster.people.map(({ uid, isOwner, displayName }) => ({ uid, isOwner, displayName })),
    };
  });
});

export const updateHouseholdMemberName = onCall(options, async request => {
  const { db, uid, home } = context(request);
  const target = request.data?.memberUid;
  let displayName;
  try {
    if (!validMemberUid(target)) throw new Error('invalid-member');
    displayName = normalizeMemberDisplayName(request.data?.displayName);
  } catch { throw new HttpsError('invalid-argument', 'Enter a display name of at most 12 characters.'); }
  return db.runTransaction(async tx => {
    const roster = await loadRoster(tx, home, uid);
    if (target !== uid && !roster.owner) throw new HttpsError('permission-denied', 'Only owner or self may edit a name.');
    checkPreview(request, roster);
    const person = roster.people.find(member => member.uid === target);
    if (!person) throw new HttpsError('not-found', 'Member is no longer present.');
    try { assertDistinctMemberName(roster.people, target, displayName); }
    catch { throw new HttpsError('already-exists', 'Display name already used.'); }
    if (person.displayName === displayName) return { updated: true };
    if (person.profileRevision >= Number.MAX_SAFE_INTEGER - 1) throw new HttpsError('failed-precondition', 'Profile revision exhausted.');
    tx.set(home.collection('memberProfiles').doc(target), {
      uid: target, displayName, revision: person.profileRevision + 1,
      updatedAt: FieldValue.serverTimestamp(),
    });
    tx.update(home, { updatedAt: FieldValue.serverTimestamp() });
    return { updated: true };
  });
});

// Deliberately retains task/occurrence UID histories. Never deletes or
// silently reassigns a departed member's chores.
export const changeHouseholdMembership = onCall(options, async request => {
  const { db, uid, home } = context(request);
  const action = request.data?.action;
  const target = request.data?.memberUid;
  if (!['LEAVE','REMOVE','TRANSFER'].includes(action) || !validMemberUid(target)) {
    throw new HttpsError('invalid-argument', 'Invalid membership action.');
  }
  return db.runTransaction(async tx => {
    const roster = await loadRoster(tx, home, uid);
    if (action === 'LEAVE' ? target !== uid : !roster.owner) {
      throw new HttpsError('permission-denied', 'Not authorized for this membership action.');
    }
    checkPreview(request, roster);
    if (target === roster.ownerUid) {
      throw new HttpsError('failed-precondition', 'The current owner cannot leave or be removed.');
    }
    if (!roster.people.some(person => person.uid === target)) {
      throw new HttpsError('not-found', 'Member is no longer present.');
    }
    const member = home.collection('members').doc(target);
    const targetIndex = db.doc('users/' + target + '/households/' + home.id);
    if (action === 'TRANSFER') {
      if (roster.transferBlocked) throw new HttpsError('failed-precondition', 'Finish and clean up migration first.');
      if (roster.revision >= Number.MAX_SAFE_INTEGER - 1) throw new HttpsError('failed-precondition', 'Ownership revision exhausted.');
      // Both index records and the owner/member representation change together.
      tx.delete(member);
      tx.create(home.collection('members').doc(uid), {
        uid, role: 'member', householdId: home.id, joinedAt: FieldValue.serverTimestamp(),
      });
      tx.set(db.doc('users/' + uid + '/households/' + home.id), {
        householdId: home.id, role: 'member', updatedAt: FieldValue.serverTimestamp(),
      });
      tx.set(targetIndex, { householdId: home.id, role: 'owner', updatedAt: FieldValue.serverTimestamp() });
      tx.update(home, {
        ownerUid: target, ownershipRevision: roster.revision + 1,
        updatedAt: FieldValue.serverTimestamp(),
      });
      return { changed: true, action };
    }
    try { requireRemovableMemberCount(roster.data.memberCount); }
    catch { throw new HttpsError('failed-precondition', 'Invalid household membership state.'); }
    tx.delete(member);
    tx.delete(targetIndex);
    tx.delete(home.collection('memberProfiles').doc(target));
    tx.update(home, { memberCount: FieldValue.increment(-1), updatedAt: FieldValue.serverTimestamp() });
    return { changed: true, action };
  });
});
