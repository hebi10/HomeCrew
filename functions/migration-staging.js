import { randomUUID } from 'node:crypto';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { getFirestore, FieldValue, Timestamp } from 'firebase-admin/firestore';
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
    publishIndex: data.publishIndex ?? 0,
    publishedDefinitions: data.publishedDefinitions ?? 0,
    publishedOccurrences: data.publishedOccurrences ?? 0,
  };
}
async function checkTarget(tx, home, house, uid, header) {
  if (house.data().migrationPublishLock != null) {
    throw new HttpsError('failed-precondition', 'Migration publication is in progress.');
  }
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
    if (!['UPLOADING','VERIFYING','SEALED'].includes(saved.state)) {
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
    const saved = checkSession(snapshot, uid, sessionId);
    if (house.data().migrationPublishLock != null ||
        ['PUBLISHING','ROLLING_BACK'].includes(saved.state)) {
      throw new HttpsError('failed-precondition', 'Finish or roll back publication first.');
    }
    if (['PUBLISHED','CLEANING'].includes(saved.state)) {
      throw new HttpsError('failed-precondition', 'Use completed staging cleanup.');
    }
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


async function checkPublishMembers(tx, home, house, uid, header) {
  const members = await tx.get(home.collection('members').limit(9));
  const ids = members.docs.filter(doc => doc.id === doc.data().uid && doc.data().role === 'member')
    .map(doc => doc.id);
  const current = new Set([uid, ...ids]);
  if (ids.length !== members.size || current.size !== house.data().memberCount ||
      current.size > 8 || !header.memberUids.every(member => current.has(member))) {
    throw new HttpsError('failed-precondition', 'Current membership no longer matches. Roll back or restore membership.');
  }
}

function readVerifiedChunk(doc, sessionId, index) {
  if (!doc.exists || doc.data().sessionId !== sessionId) {
    throw new HttpsError('failed-precondition', 'Sealed chunk missing.');
  }
  try {
    const chunk = JSON.parse(doc.data().payloadJson);
    const verified = stageChunk(chunk);
    if (chunk.index !== index || verified.digest !== doc.data().digest ||
        verified.bytes !== doc.data().bytes) throw new Error('chunk-mismatch');
    return chunk;
  } catch { throw new HttpsError('failed-precondition', 'Sealed chunk changed.'); }
}

function lockMatches(house, sessionId, digest) {
  const lock = house.data().migrationPublishLock;
  return lock?.sessionId === sessionId && lock?.digest === digest;
}

function targetRows(home, chunk, sessionId, digest, uid) {
  const definitions = chunk.definitions.map(definition => {
    const { sourceCreatedAtEpochMillis, sourceUpdatedAtEpochMillis, ...row } = definition;
    return { ref: home.collection('chores').doc(row.id), data: {
      ...row, sourceCreatedAtEpochMillis, sourceUpdatedAtEpochMillis,
      createdAt: Timestamp.fromMillis(sourceCreatedAtEpochMillis),
      updatedAt: Timestamp.fromMillis(sourceUpdatedAtEpochMillis),
      creatorUid: uid, revision: 1, migrationDigest: digest, migrationStageSessionId: sessionId,
    } };
  });
  const occurrences = chunk.occurrences.map(occurrence => {
    const { id, completedAtEpochMillis, skippedAtEpochMillis, ...row } = occurrence;
    return { ref: home.collection('occurrences').doc(id), data: {
      ...row, completedAtEpochMillis, skippedAtEpochMillis,
      completedAt: completedAtEpochMillis === null ? null : Timestamp.fromMillis(completedAtEpochMillis),
      skippedAt: skippedAtEpochMillis === null ? null : Timestamp.fromMillis(skippedAtEpochMillis),
      revision: 1, scheduleCancelledAt: null, migrationDigest: digest, migrationStageSessionId: sessionId,
      createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    } };
  });
  return [...definitions, ...occurrences];
}

// A short transaction per sealed chunk. The very first chunk sets the
// household visibility/write barrier in the SAME transaction. The final chunk
// creates the receipt and removes it atomically. Repeated indices are no-ops.
export const publishLocalMigrationStageChunk = onCall(options, async request => {
  const { db, home, session, uid, sessionId } = context(request);
  const index = request.data?.index;
  const digest = request.data?.digest;
  if (!Number.isSafeInteger(index) || index < 0 || index >= 2048 || !validStageDigest(digest)) {
    throw new HttpsError('invalid-argument', 'Invalid publication step.');
  }
  const receipt = home.collection('operations').doc('local-migration-' + digest);
  return db.runTransaction(async tx => {
    const [house, snapshot, previousReceipt] = await Promise.all([
      tx.get(home), tx.get(session), tx.get(receipt),
    ]);
    checkOwner(house, uid);
    if (previousReceipt.exists) {
      const saved = previousReceipt.data();
      if (saved.actorUid !== uid || saved.type !== 'local-migration-v1' ||
          saved.sessionId !== sessionId || saved.digest !== digest) {
        throw new HttpsError('already-exists', 'Migration receipt differs.');
      }
      return { published: true, replayed: true, ...saved.result };
    }
    const saved = checkSession(snapshot, uid, sessionId);
    if (saved.header.digest !== digest || !['SEALED','PUBLISHING'].includes(saved.state)) {
      throw new HttpsError('failed-precondition', 'Migration must be sealed first.');
    }
    const current = saved.publishIndex ?? 0;
    if (saved.state === 'SEALED') {
      if (index !== 0 || current !== 0) throw new HttpsError('failed-precondition', 'Begin with the first chunk.');
      await checkTarget(tx, home, house, uid, saved.header);
    } else {
      if (!lockMatches(house, sessionId, digest)) throw new HttpsError('failed-precondition', 'Publication lock mismatch.');
      await checkPublishMembers(tx, home, house, uid, saved.header);
    }
    if (index < current) return { ...status(saved), published: false, replayed: true };
    if (index !== current || index >= saved.header.chunkCount) {
      throw new HttpsError('failed-precondition', 'Publish the next missing chunk.');
    }
    const chunk = readVerifiedChunk(await tx.get(session.collection('migrationChunks').doc(chunkId(index))), sessionId, index);
    const rows = targetRows(home, chunk, sessionId, digest, uid);
    const occupied = await tx.getAll(...rows.map(row => row.ref));
    if (occupied.some(doc => doc.exists)) throw new HttpsError('already-exists', 'Publication target occupied.');
    const progress = {
      publishIndex: current + 1,
      publishedDefinitions: (saved.publishedDefinitions ?? 0) + chunk.definitions.length,
      publishedOccurrences: (saved.publishedOccurrences ?? 0) + chunk.occurrences.length,
      publishedCompleted: (saved.publishedCompleted ?? 0) + chunk.occurrences.filter(row => row.completed).length,
      publishedSkipped: (saved.publishedSkipped ?? 0) + chunk.occurrences.filter(row => row.skippedAtEpochMillis !== null).length,
    };
    const finished = progress.publishIndex === saved.header.chunkCount;
    if (progress.publishedDefinitions > saved.header.definitionCount ||
        progress.publishedOccurrences > saved.header.occurrenceCount ||
        finished && (progress.publishedDefinitions !== saved.header.definitionCount ||
          progress.publishedOccurrences !== saved.header.occurrenceCount)) {
      throw new HttpsError('failed-precondition', 'Publication counts differ.');
    }
    for (const row of rows) tx.create(row.ref, row.data);
    const nextState = finished ? 'PUBLISHED' : 'PUBLISHING';
    tx.update(session, { ...progress, state: nextState, updatedAt: FieldValue.serverTimestamp() });
    tx.update(home, {
      migrationPublishLock: finished ? FieldValue.delete() : { sessionId, digest },
      updatedAt: FieldValue.serverTimestamp(),
    });
    if (!finished) return { ...status({ ...saved, ...progress, state: nextState }), published: false, replayed: false };
    const result = {
      digest, definitions: progress.publishedDefinitions, occurrences: progress.publishedOccurrences,
      completed: progress.publishedCompleted, skipped: progress.publishedSkipped,
    };
    tx.create(receipt, { type: 'local-migration-v1', actorUid: uid, digest, sessionId, result,
      defaultMemberUid: saved.header.defaultMemberUid, memberUids: saved.header.memberUids,
      createdAt: FieldValue.serverTimestamp() });
    return { published: true, replayed: false, ...result };
  });
});

// Explicit recovery only: remove a published prefix in reverse chunk order.
// Never delete an unrelated or already-visible record. The lock remains until
// the original empty target is verified in the final rollback transaction.
export const rollbackLocalMigrationPublication = onCall(options, async request => {
  const { db, home, session, uid, sessionId } = context(request);
  return db.runTransaction(async tx => {
    const [house, snapshot] = await Promise.all([tx.get(home), tx.get(session)]);
    checkOwner(house, uid);
    const saved = checkSession(snapshot, uid, sessionId);
    const digest = saved.header.digest;
    if (saved.state === 'ROLLED_BACK') return { ...status(saved), rolledBack: true };
    if (!['PUBLISHING','ROLLING_BACK'].includes(saved.state) || !lockMatches(house, sessionId, digest)) {
      throw new HttpsError('failed-precondition', 'Only a hidden partial publication can be rolled back.');
    }
    const current = saved.publishIndex;
    if (!Number.isSafeInteger(current) || current < 1) throw new HttpsError('failed-precondition', 'Invalid rollback progress.');
    const index = current - 1;
    const chunk = readVerifiedChunk(await tx.get(session.collection('migrationChunks').doc(chunkId(index))), sessionId, index);
    const rows = targetRows(home, chunk, sessionId, digest, uid);
    const docs = await tx.getAll(...rows.map(row => row.ref));
    if (docs.some(doc => !doc.exists || doc.data().migrationStageSessionId !== sessionId ||
        doc.data().migrationDigest !== digest || doc.data().revision !== 1)) {
      throw new HttpsError('failed-precondition', 'Unexpected target record. Keep publication locked.');
    }
    if (index === 0) {
      // No foreign records may become visible on unlocking.
      const [definitions, occurrences] = await Promise.all([
        tx.get(home.collection('chores').limit(chunk.definitions.length + 1)),
        tx.get(home.collection('occurrences').limit(chunk.occurrences.length + 1)),
      ]);
      if (definitions.size !== chunk.definitions.length || occurrences.size !== chunk.occurrences.length ||
          [...definitions.docs, ...occurrences.docs].some(doc => !rows.some(row => row.ref.path === doc.ref.path))) {
        throw new HttpsError('failed-precondition', 'Unexpected records remain. Keep publication locked.');
      }
    }
    for (const row of rows) tx.delete(row.ref);
    const progress = {
      publishIndex: index,
      publishedDefinitions: saved.publishedDefinitions - chunk.definitions.length,
      publishedOccurrences: saved.publishedOccurrences - chunk.occurrences.length,
      publishedCompleted: saved.publishedCompleted - chunk.occurrences.filter(row => row.completed).length,
      publishedSkipped: saved.publishedSkipped - chunk.occurrences.filter(row => row.skippedAtEpochMillis !== null).length,
    };
    const state = index === 0 ? 'ROLLED_BACK' : 'ROLLING_BACK';
    tx.update(session, { ...progress, state, updatedAt: FieldValue.serverTimestamp() });
    if (index === 0) tx.update(home, { migrationPublishLock: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    return { ...status({ ...saved, ...progress, state }), rolledBack: index === 0 };
  });
});

// Publication is final. Cleanup only private chunks; preserve the receipt and
// all live records. Repeating after deletion is safe.
export const cleanupPublishedMigrationStage = onCall(options, async request => {
  const { db, home, session, uid, sessionId } = context(request);
  return db.runTransaction(async tx => {
    const [house, snapshot] = await Promise.all([tx.get(home), tx.get(session)]);
    checkOwner(house, uid);
    if (!snapshot.exists) return { removed: true, remaining: false };
    const saved = checkSession(snapshot, uid, sessionId);
    if (!['PUBLISHED','CLEANING'].includes(saved.state) || house.data().migrationPublishLock != null) {
      throw new HttpsError('failed-precondition', 'Only published staging can be cleaned.');
    }
    const receipt = await tx.get(home.collection('operations').doc('local-migration-' + saved.header.digest));
    if (!receipt.exists || receipt.data().sessionId !== sessionId || receipt.data().actorUid !== uid) {
      throw new HttpsError('failed-precondition', 'Missing publication receipt.');
    }
    const chunks = await tx.get(session.collection('migrationChunks').limit(CLEANUP_BATCH + 1));
    for (const doc of chunks.docs.slice(0, CLEANUP_BATCH)) tx.delete(doc.ref);
    if (chunks.size <= CLEANUP_BATCH) {
      tx.delete(session);
      return { removed: true, remaining: false };
    }
    tx.update(session, { state: 'CLEANING', updatedAt: FieldValue.serverTimestamp() });
    return { removed: false, remaining: true };
  });
});
