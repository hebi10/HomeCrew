import { randomUUID } from 'node:crypto';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { canonicalMigrationDigest } from './migration-core.js';
import { requireDocumentId } from './sync-core.js';
import { stageHeader, stageChunk, appendStageCounts, assembleStagedManifest,
  validStageDigest, validSessionId } from './migration-staging-core.js';

const options = { region: 'asia-northeast3', timeoutSeconds: 60, maxInstances: 5 };
const sealOptions = { ...options, timeoutSeconds: 180, memory: '512MiB', concurrency: 1, maxInstances: 2 };
const chunkId = index => String(index).padStart(4, '0');
const CLEANUP_BATCH = 50;

function context(request, needsSession = true) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Firebase sign-in required.');
  let householdId;
  try {
    householdId = requireDocumentId(request.data?.householdId);
    if (needsSession && !validSessionId(request.data?.sessionId)) throw new Error('invalid-session');
  } catch { throw new HttpsError('invalid-argument', 'Invalid staging identifier.'); }
  const db = getFirestore();
  const home = db.collection('households').doc(householdId);
  const session = home.collection('migrationStaging').doc('current');
  return { db, home, session, uid, sessionId: request.data?.sessionId };
}
function checkOwner(house, uid) {
  if (!house.exists || house.data().ownerUid !== uid) {
    throw new HttpsError('permission-denied', 'Household owner required.');
  }
}
function checkSession(snapshot, uid, sessionId) {
  if (!snapshot.exists || snapshot.data().ownerUid !== uid || snapshot.data().sessionId !== sessionId) {
    throw new HttpsError('not-found', 'Migration staging session not found.');
  }
  return snapshot.data();
}
function status(data) {
  return {
    found: true, sessionId: data.sessionId, digest: data.header.digest,
    state: data.state, nextIndex: data.nextIndex, chunkCount: data.header.chunkCount,
    receivedDefinitions: data.receivedDefinitions, definitionCount: data.header.definitionCount,
    receivedOccurrences: data.receivedOccurrences, occurrenceCount: data.header.occurrenceCount,
    receivedBytes: data.receivedBytes,
  };
}
async function checkTarget(tx, home, house, uid, header) {
  const [members, definitions, occurrences] = await Promise.all([
    tx.get(home.collection('members').limit(9)),
    tx.get(home.collection('chores').limit(1)),
    tx.get(home.collection('occurrences').limit(1)),
  ]);
  const memberIds = members.docs.filter(doc =>
    doc.id === doc.data().uid && doc.data().role === 'member').map(doc => doc.id);
  const current = new Set([uid, ...memberIds]);
  if (current.size > 8 || current.size !== house.data().memberCount ||
      memberIds.length !== members.size || !header.memberUids.every(member => current.has(member))) {
    throw new HttpsError('failed-precondition', 'Current membership no longer matches.');
  }
  if (!definitions.empty || !occurrences.empty) {
    throw new HttpsError('failed-precondition', 'Migration target must remain empty.');
  }
}

export const beginLocalMigrationStage = onCall(options, async request => {
  const { db, home, session, uid } = context(request, false);
  let header, headerDigest;
  try {
    header = stageHeader(request.data?.header, home.id, uid);
    headerDigest = canonicalMigrationDigest(header);
  } catch { throw new HttpsError('invalid-argument', 'Invalid staging header.'); }
  const newSessionId = randomUUID();
  return db.runTransaction(async tx => {
    const [house, previous] = await Promise.all([tx.get(home), tx.get(session)]);
    checkOwner(house, uid);
    if (previous.exists) {
      const saved = previous.data();
      if (saved.ownerUid !== uid || saved.headerDigest !== headerDigest) {
        throw new HttpsError('already-exists', 'Another migration is preserved.');
      }
      if (saved.state === 'CANCELLING') throw new HttpsError('failed-precondition', 'Finish cancellation first.');
      return status(saved);
    }
    await checkTarget(tx, home, house, uid, header);
    // Cleanup must delete all child chunks before deleting the session document.
    const leftover = await tx.get(session.collection('migrationChunks').limit(1));
    if (!leftover.empty) throw new HttpsError('failed-precondition', 'Previous cleanup is incomplete.');
    const saved = { ownerUid: uid, sessionId: newSessionId, header, headerDigest,
      state: 'UPLOADING', nextIndex: 0, receivedDefinitions: 0, receivedOccurrences: 0, receivedBytes: 0 };
    tx.create(session, { ...saved, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp() });
    return status(saved);
  });
});

export const getLocalMigrationStage = onCall(options, async request => {
  const { db, home, session, uid } = context(request, false);
  return db.runTransaction(async tx => {
    const [house, saved] = await Promise.all([tx.get(home), tx.get(session)]);
    checkOwner(house, uid);
    if (!saved.exists) return { found: false };
    if (saved.data().ownerUid !== uid) throw new HttpsError('permission-denied', 'Session owner required.');
    return status(saved.data());
  });
});

export const appendLocalMigrationStageChunk = onCall(options, async request => {
  const { db, home, session, uid, sessionId } = context(request);
  let chunk, index;
  try {
    chunk = stageChunk(request.data?.chunk);
    index = request.data.chunk.index;
    if (!validStageDigest(request.data?.chunkDigest) || request.data.chunkDigest !== chunk.digest) {
      throw new Error('digest-mismatch');
    }
  } catch { throw new HttpsError('invalid-argument', 'Invalid or oversized staging chunk.'); }
  const ref = session.collection('migrationChunks').doc(chunkId(index));
  return db.runTransaction(async tx => {
    const [house, snapshot, existing] = await Promise.all([tx.get(home), tx.get(session), tx.get(ref)]);
    checkOwner(house, uid);
    const saved = checkSession(snapshot, uid, sessionId);
    if (saved.state === 'CANCELLING' || saved.state === 'INVALID') {
      throw new HttpsError('failed-precondition', 'Staging is closed.');
    }
    await checkTarget(tx, home, house, uid, saved.header);
    if (existing.exists) {
      const previous = existing.data();
      if (previous.sessionId !== sessionId || previous.digest !== chunk.digest ||
          previous.payloadJson !== chunk.payloadJson && canonicalMigrationDigest(JSON.parse(previous.payloadJson)) !== chunk.digest ||
          index >= saved.nextIndex) {
        throw new HttpsError('already-exists', 'Chunk differs from preserved data.');
      }
      return { ...status(saved), replayed: true };
    }
    if (saved.state !== 'UPLOADING' || index !== saved.nextIndex) {
      throw new HttpsError('failed-precondition', 'Upload the next missing chunk in order.');
    }
    let progress;
    try { progress = appendStageCounts(saved.header, saved, chunk); }
    catch { throw new HttpsError('invalid-argument', 'Staging counts or size limit exceeded.'); }
    tx.create(ref, { sessionId, digest: chunk.digest, payloadJson: chunk.payloadJson, bytes: chunk.bytes });
    tx.update(session, { ...progress, updatedAt: FieldValue.serverTimestamp() });
    return { ...status({ ...saved, ...progress }), replayed: false };
  });
});

export const sealLocalMigrationStage = onCall(sealOptions, async request => {
  const { db, home, session, uid, sessionId } = context(request);
  // Freeze uploads before reading immutable chunks. Cancellation may proceed
  // concurrently; the session nonce is checked again before marking sealed.
  const frozen = await db.runTransaction(async tx => {
    const [house, snapshot] = await Promise.all([tx.get(home), tx.get(session)]);
    checkOwner(house, uid);
    const saved = checkSession(snapshot, uid, sessionId);
    await checkTarget(tx, home, house, uid, saved.header);
    if (saved.state === 'SEALED') return saved;
    if (!['UPLOADING','VERIFYING'].includes(saved.state) ||
        saved.nextIndex !== saved.header.chunkCount ||
        saved.receivedDefinitions !== saved.header.definitionCount ||
        saved.receivedOccurrences !== saved.header.occurrenceCount) {
      throw new HttpsError('failed-precondition', 'Upload all chunks before verification.');
    }
    if (saved.state === 'UPLOADING') tx.update(session, { state: 'VERIFYING', updatedAt: FieldValue.serverTimestamp() });
    return { ...saved, state: 'VERIFYING' };
  });
  if (frozen.state === 'SEALED') return status(frozen);
  const chunks = [];
  let invalid = false;
  // Bounded page size avoids one enormous Firestore RPC. The full validated
  // dataset is bounded to 16MiB, and no active document is written here.
  for (let from = 0; from < frozen.header.chunkCount && !invalid; from += 25) {
    const refs = Array.from({ length: Math.min(25, frozen.header.chunkCount - from) },
      (_, offset) => session.collection('migrationChunks').doc(chunkId(from + offset)));
    const docs = await db.getAll(...refs);
    for (let offset = 0; offset < docs.length; offset++) {
      const doc = docs[offset];
      try {
        if (!doc.exists || doc.data().sessionId !== sessionId) throw new Error('missing-chunk');
        const chunk = JSON.parse(doc.data().payloadJson);
        const verified = stageChunk(chunk);
        if (chunk.index !== from + offset || verified.digest !== doc.data().digest ||
            verified.bytes !== doc.data().bytes) throw new Error('chunk-mismatch');
        chunks.push(chunk);
      } catch { invalid = true; break; }
    }
  }
  if (!invalid) {
    try { assembleStagedManifest(frozen.header, chunks); }
    catch { invalid = true; }
  }
  const result = await db.runTransaction(async tx => {
    const [house, snapshot] = await Promise.all([tx.get(home), tx.get(session)]);
    checkOwner(house, uid);
    const saved = checkSession(snapshot, uid, sessionId);
    if (saved.headerDigest !== frozen.headerDigest || !['VERIFYING','SEALED','INVALID'].includes(saved.state)) {
      throw new HttpsError('aborted', 'Staging session changed.');
    }
    if (saved.state === 'SEALED' || saved.state === 'INVALID') return status(saved);
    await checkTarget(tx, home, house, uid, saved.header);
    const state = invalid ? 'INVALID' : 'SEALED';
    tx.update(session, { state, updatedAt: FieldValue.serverTimestamp() });
    return status({ ...saved, state });
  });
  if (result.state === 'INVALID') throw new HttpsError('invalid-argument', 'Full migration validation failed. Cancel the staged copy before retrying.');
  return result;
});

export const cancelLocalMigrationStage = onCall(options, async request => {
  const { db, home, session, uid, sessionId } = context(request);
  return db.runTransaction(async tx => {
    const [house, snapshot] = await Promise.all([tx.get(home), tx.get(session)]);
    checkOwner(house, uid);
    if (!snapshot.exists) return { removed: true, remaining: false };
    checkSession(snapshot, uid, sessionId);
    const chunks = await tx.get(session.collection('migrationChunks').limit(CLEANUP_BATCH + 1));
    for (const doc of chunks.docs.slice(0, CLEANUP_BATCH)) tx.delete(doc.ref);
    if (chunks.size <= CLEANUP_BATCH) {
      tx.delete(session);
      return { removed: true, remaining: false };
    }
    tx.update(session, { state: 'CANCELLING', updatedAt: FieldValue.serverTimestamp() });
    return { removed: false, remaining: true };
  });
});
