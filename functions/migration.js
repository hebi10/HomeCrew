import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { getFirestore, FieldValue, Timestamp } from 'firebase-admin/firestore';
import { canonicalMigrationDigest, validateAtomicMigration } from './migration-core.js';
import { requireDocumentId } from './sync-core.js';

const options = { region: 'asia-northeast3', timeoutSeconds: 60, maxInstances: 5 };
const stamp = value => value === null ? null : Timestamp.fromMillis(value);

// One deliberately bounded transaction; no partial writes, staging collections
// or overwrite mode. Large manifests are rejected before touching the database.
export const importLocalHouseholdManifest = onCall(options, async request => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Firebase sign-in required.');
  let manifest, digest, householdId;
  try {
    householdId = requireDocumentId(request.data?.householdId);
    digest = request.data?.digest;
    if (typeof digest !== 'string' || !/^[a-f0-9]{64}$/.test(digest)) throw new Error('invalid-digest');
    manifest = validateAtomicMigration(request.data?.manifest, householdId, uid);
    if (canonicalMigrationDigest(manifest) !== digest) throw new Error('digest-mismatch');
  } catch {
    throw new HttpsError('invalid-argument', 'Invalid or oversized migration manifest.');
  }
  const db = getFirestore();
  const home = db.collection('households').doc(householdId);
  const receipt = home.collection('operations').doc('local-migration-' + digest);
  return db.runTransaction(async tx => {
    const [house, existingReceipt] = await Promise.all([tx.get(home), tx.get(receipt)]);
    if (!house.exists || house.data().ownerUid !== uid) {
      throw new HttpsError('permission-denied', 'Household owner required.');
    }
    if (existingReceipt.exists) {
      const previous = existingReceipt.data();
      if (previous.actorUid !== uid || previous.digest !== digest || previous.type !== 'local-migration-v1') {
        throw new HttpsError('already-exists', 'Migration receipt mismatch.');
      }
      return { ...previous.result, replayed: true };
    }
    const [members, chores, occurrences] = await Promise.all([
      tx.get(home.collection('members').limit(9)),
      tx.get(home.collection('chores').limit(1)),
      tx.get(home.collection('occurrences').limit(1)),
    ]);
    if (!chores.empty || !occurrences.empty) {
      throw new HttpsError('failed-precondition', 'Migration target must be empty.');
    }
    const memberIds = members.docs.filter(doc =>
      doc.data().uid === doc.id && doc.data().role === 'member').map(doc => doc.id);
    const current = new Set([uid, ...memberIds]);
    if (current.size > 8 || current.size !== house.data().memberCount ||
        memberIds.length !== members.size || !manifest.memberUids.every(member => current.has(member))) {
      throw new HttpsError('failed-precondition', 'Current membership no longer matches.');
    }
    tx.update(home, { updatedAt: FieldValue.serverTimestamp() });
    for (const definition of manifest.definitions) {
      const { sourceCreatedAtEpochMillis, sourceUpdatedAtEpochMillis, ...row } = definition;
      tx.create(home.collection('chores').doc(row.id), {
        ...row, sourceCreatedAtEpochMillis, sourceUpdatedAtEpochMillis,
        createdAt: Timestamp.fromMillis(sourceCreatedAtEpochMillis),
        updatedAt: Timestamp.fromMillis(sourceUpdatedAtEpochMillis),
        creatorUid: uid, revision: 1, migrationDigest: digest,
      });
    }
    for (const occurrence of manifest.occurrences) {
      const { id, completedAtEpochMillis, skippedAtEpochMillis, ...row } = occurrence;
      tx.create(home.collection('occurrences').doc(id), {
        ...row, completedAtEpochMillis, skippedAtEpochMillis,
        completedAt: stamp(completedAtEpochMillis), skippedAt: stamp(skippedAtEpochMillis),
        revision: 1, scheduleCancelledAt: null, migrationDigest: digest,
        createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      });
    }
    const result = {
      digest, definitions: manifest.definitions.length, occurrences: manifest.occurrences.length,
      completed: manifest.occurrences.filter(row => row.completed).length,
      skipped: manifest.occurrences.filter(row => row.skippedAtEpochMillis !== null).length,
    };
    tx.create(receipt, {
      type: 'local-migration-v1', actorUid: uid, digest, result,
      defaultMemberUid: manifest.defaultMemberUid, memberUids: manifest.memberUids,
      createdAt: FieldValue.serverTimestamp(),
    });
    return { ...result, replayed: false };
  });
});


// Read-only recovery: no manifest transmission or mutation required to check
// whether a previously staged import committed.
export const getLocalHouseholdMigrationReceipt = onCall(options, async request => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Firebase sign-in required.');
  let householdId, digest;
  try {
    householdId = requireDocumentId(request.data?.householdId);
    digest = request.data?.digest;
    if (typeof digest !== 'string' || !/^[a-f0-9]{64}$/.test(digest)) throw new Error('invalid-digest');
  } catch { throw new HttpsError('invalid-argument', 'Invalid receipt identifier.'); }
  const home = getFirestore().collection('households').doc(householdId);
  return getFirestore().runTransaction(async tx => {
    const [house, receipt] = await Promise.all([
      tx.get(home), tx.get(home.collection('operations').doc('local-migration-' + digest)),
    ]);
    if (!house.exists || house.data().ownerUid !== uid) {
      throw new HttpsError('permission-denied', 'Household owner required.');
    }
    if (!receipt.exists) return { found: false };
    const saved = receipt.data();
    if (saved.actorUid !== uid || saved.digest !== digest || saved.type !== 'local-migration-v1') {
      throw new HttpsError('permission-denied', 'Receipt ownership required.');
    }
    return { ...saved.result, found: true };
  });
});
