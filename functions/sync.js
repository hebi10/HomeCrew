import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { createHash } from 'node:crypto';
import {
  requireDocumentId, normalizeRemoteChore, nextRevision, mutationDigest,
  remoteOccurrenceId, validateScheduledEpochDay,
} from './sync-core.js';

import { normalizeRemoteSchedule, scheduledAssignee, scheduleWindow } from './recurrence-core.js';

const options = { region: 'asia-northeast3', timeoutSeconds: 30, maxInstances: 10 };

function requireSignedIn(request) {
  if (!request.auth?.uid) throw new HttpsError('unauthenticated', 'Firebase sign-in required.');
  return request.auth.uid;
}
function homeRef(householdId) {
  try { return getFirestore().doc('households/' + requireDocumentId(householdId)); }
  catch { throw new HttpsError('invalid-argument', 'Invalid household identifier.'); }
}
function requireAuthorisedHousehold(home, member, uid) {
  if (!home.exists) throw new HttpsError('not-found', 'Household not found.');
  if (home.data().ownerUid !== uid && !member.exists) {
    throw new HttpsError('permission-denied', 'Household membership required.');
  }
}
function validatedChore(raw) {
  try { return normalizeRemoteChore(raw); }
  catch { throw new HttpsError('invalid-argument', 'Invalid chore payload.'); }
}
async function checkAssignee(tx, home, assigneeUid, ownerUid) {
  if (assigneeUid !== ownerUid) {
    const assignee = await tx.get(home.collection('members').doc(assigneeUid));
    if (!assignee.exists) throw new HttpsError('failed-precondition', 'Assignee is not a member.');
  }
}

export const createRemoteChore = onCall(options, async (request) => {
  const uid = requireSignedIn(request);
  const home = homeRef(request.data?.householdId);
  const chore = validatedChore(request.data?.chore);
  const task = home.collection('chores').doc(chore.id);
  return getFirestore().runTransaction(async (tx) => {
    const [homeSnap, actor, existing] = await Promise.all([
      tx.get(home), tx.get(home.collection('members').doc(uid)), tx.get(task),
    ]);
    requireAuthorisedHousehold(homeSnap, actor, uid);
    if (existing.exists) {
      const stored = existing.data();
      if (stored.creatorUid === uid && Object.keys(chore).every(k => stored[k] === chore[k])) {
        return { choreId: chore.id, revision: stored.revision, replayed: true };
      }
      throw new HttpsError('already-exists', 'Chore already exists. Resolve before importing.');
    }
    await checkAssignee(tx, home, chore.assigneeUid, homeSnap.data().ownerUid);
    tx.create(task, {
      ...chore, creatorUid: uid, revision: 1, createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { choreId: chore.id, revision: 1, replayed: false };
  });
});

export const updateRemoteChore = onCall(options, async (request) => {
  const uid = requireSignedIn(request);
  const home = homeRef(request.data?.householdId);
  const chore = validatedChore(request.data?.chore);
  const task = home.collection('chores').doc(chore.id);
  const expected = request.data?.expectedRevision;
  return getFirestore().runTransaction(async (tx) => {
    const [homeSnap, actor, old] = await Promise.all([
      tx.get(home), tx.get(home.collection('members').doc(uid)), tx.get(task),
    ]);
    requireAuthorisedHousehold(homeSnap, actor, uid);
    if (!old.exists) throw new HttpsError('not-found', 'Chore not found.');
    let revision;
    try { revision = nextRevision(old.data().revision, expected); }
    catch { throw new HttpsError('aborted', 'Chore was changed on another device. Reload before editing.'); }
    if (old.data().schedule && chore.assigneeUid !== old.data().assigneeUid) {
      throw new HttpsError('failed-precondition', 'Update the shared schedule to change assignment.');
    }
    await checkAssignee(tx, home, chore.assigneeUid, homeSnap.data().ownerUid);
    tx.update(task, {
      ...chore, revision, updatedAt: FieldValue.serverTimestamp(),
    });
    return { choreId: chore.id, revision };
  });
});

export const ensureRemoteOccurrence = onCall(options, async (request) => {
  const uid = requireSignedIn(request);
  const home = homeRef(request.data?.householdId);
  let choreId, scheduledEpochDay;
  try {
    choreId = requireDocumentId(request.data?.choreId);
    scheduledEpochDay = request.data?.scheduledEpochDay;
    if (!Number.isSafeInteger(scheduledEpochDay)) throw new Error('invalid-occurrence-date');
  } catch {
    throw new HttpsError('invalid-argument', 'Invalid occurrence or scheduling date.');
  }
  const task = home.collection('chores').doc(choreId);
  const occurrenceId = remoteOccurrenceId(choreId, scheduledEpochDay);
  const occurrence = home.collection('occurrences').doc(occurrenceId);

  return getFirestore().runTransaction(async (tx) => {
    const [homeSnap, actor, definition, existing] = await Promise.all([
      tx.get(home), tx.get(home.collection('members').doc(uid)),
      tx.get(task), tx.get(occurrence),
    ]);
    requireAuthorisedHousehold(homeSnap, actor, uid);
    if (!definition.exists) throw new HttpsError('not-found', 'Chore definition not found.');
    if (existing.exists) {
      if (existing.data().definitionId !== choreId ||
          existing.data().scheduledEpochDay !== scheduledEpochDay) {
        throw new HttpsError('already-exists', 'Occurrence ID collision.');
      }
      return { occurrenceId, revision: existing.data().revision, replayed: true };
    }
    // A retry of a committed occurrence must remain valid after midnight.
    // Apply the 62-day creation window only if no occurrence exists.
    try { validateScheduledEpochDay(scheduledEpochDay); }
    catch { throw new HttpsError('invalid-argument', 'Outside the creation window.'); }
    let assigneeUid = definition.data().assigneeUid;
    if (definition.data().schedule) {
      try { assigneeUid = scheduledAssignee(normalizeRemoteSchedule(definition.data().schedule), scheduledEpochDay); }
      catch { throw new HttpsError('failed-precondition', 'Date is outside the configured schedule.'); }
    }
    await checkAssignee(tx, home, assigneeUid, homeSnap.data().ownerUid);
    tx.create(occurrence, {
      definitionId: choreId, scheduledEpochDay, assignedUid: assigneeUid,
      completed: false, completedByUid: null, completedAt: null, skippedAt: null,
      revision: 1, createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { occurrenceId, revision: 1, replayed: false };
  });
});

export const recordChoreCompletion = onCall(options, async (request) => {
  const uid = requireSignedIn(request);
  const home = homeRef(request.data?.householdId);
  let occurrenceId;
  try { occurrenceId = requireDocumentId(request.data?.occurrenceId); }
  catch { throw new HttpsError('invalid-argument', 'Invalid occurrence ID.'); }
  const completed = request.data?.completed;
  const expected = request.data?.expectedRevision;
  if (typeof completed !== 'boolean') {
    throw new HttpsError('invalid-argument', 'Completion status must be a boolean.');
  }
  let digest;
  try {
    digest = mutationDigest(home.id, uid, request.data?.requestId, occurrenceId, completed);
  } catch { throw new HttpsError('invalid-argument', 'Request ID is required.'); }
  const opId = createHash('sha256').update(
    home.id + ':' + uid + ':' + request.data.requestId,
  ).digest('hex');
  const op = home.collection('operations').doc(opId);
  const occurrence = home.collection('occurrences').doc(occurrenceId);
  return getFirestore().runTransaction(async (tx) => {
    const [homeSnap, actor, old, previous] = await Promise.all([
      tx.get(home), tx.get(home.collection('members').doc(uid)),
      tx.get(occurrence), tx.get(op),
    ]);
    requireAuthorisedHousehold(homeSnap, actor, uid);
    if (previous.exists) {
      if (previous.data().digest !== digest) {
        throw new HttpsError('already-exists', 'Mutation key reused with different data.');
      }
      return { occurrenceId, revision: previous.data().revision, replayed: true };
    }
    if (!old.exists) throw new HttpsError('not-found', 'Occurrence not found.');
    if (old.data().skippedAt != null) {
      throw new HttpsError('failed-precondition', 'Skipped chores cannot be completed.');
    }
    let revision;
    try { revision = nextRevision(old.data().revision, expected); }
    catch { throw new HttpsError('aborted', 'Completion changed elsewhere. Reload and retry.'); }
    tx.update(occurrence, {
      completed, completedByUid: completed ? uid : null,
      completedAt: completed ? FieldValue.serverTimestamp() : null,
      revision, updatedAt: FieldValue.serverTimestamp(),
    });
    tx.create(op, {
      actorUid: uid, occurrenceId, digest, revision,
      type: 'completion', createdAt: FieldValue.serverTimestamp(),
    });
    return { occurrenceId, revision, replayed: false };
  });
});


// Schedule changes are revision-checked. Existing occurrences (including
// future ones) are never deleted, reassigned, or reset by this foundation.
export const configureRemoteSchedule = onCall(options, async (request) => {
  const uid = requireSignedIn(request);
  const home = homeRef(request.data?.householdId);
  let choreId, schedule;
  try {
    choreId = requireDocumentId(request.data?.choreId);
    schedule = normalizeRemoteSchedule(request.data?.schedule);
  } catch { throw new HttpsError('invalid-argument', 'Invalid or unsupported schedule.'); }
  const task = home.collection('chores').doc(choreId);
  return getFirestore().runTransaction(async tx => {
    const [house, actor, definition] = await Promise.all([
      tx.get(home), tx.get(home.collection('members').doc(uid)), tx.get(task),
    ]);
    requireAuthorisedHousehold(house, actor, uid);
    if (!definition.exists) throw new HttpsError('not-found', 'Chore not found.');
    let revision;
    try { revision = nextRevision(definition.data().revision, request.data?.expectedRevision); }
    catch { throw new HttpsError('aborted', 'Schedule changed elsewhere. Reload before editing.'); }
    await Promise.all(schedule.memberUids.map(member =>
      checkAssignee(tx, home, member, house.data().ownerUid)));
    tx.update(task, {
      schedule, assigneeUid: schedule.memberUids[0], revision,
      updatedAt: FieldValue.serverTimestamp(),
    });
    return { choreId, revision, existingOccurrencesPreserved: true };
  });
});

export const generateRemoteSchedule = onCall(options, async (request) => {
  const uid = requireSignedIn(request);
  const home = homeRef(request.data?.householdId);
  let choreId;
  try { choreId = requireDocumentId(request.data?.choreId); }
  catch { throw new HttpsError('invalid-argument', 'Invalid chore identifier.'); }
  const from = request.data?.fromEpochDay, to = request.data?.toEpochDay;
  if (!Number.isSafeInteger(from) || !Number.isSafeInteger(to) || to < from || to - from > 62) {
    throw new HttpsError('invalid-argument', 'Invalid schedule window.');
  }
  const task = home.collection('chores').doc(choreId);
  return getFirestore().runTransaction(async tx => {
    const [house, actor, definition] = await Promise.all([
      tx.get(home), tx.get(home.collection('members').doc(uid)), tx.get(task),
    ]);
    requireAuthorisedHousehold(house, actor, uid);
    if (!definition.exists) throw new HttpsError('not-found', 'Chore not found.');
    if (!Number.isSafeInteger(request.data?.expectedRevision) ||
        request.data.expectedRevision !== definition.data().revision) {
      throw new HttpsError('aborted', 'Schedule changed elsewhere.');
    }
    let schedule, planned;
    try {
      schedule = normalizeRemoteSchedule(definition.data().schedule);
      planned = scheduleWindow(schedule, from, to);
    } catch { throw new HttpsError('failed-precondition', 'No valid schedule for this window.'); }
    if (planned.length === 0) return { created: 0, existing: 0 };
    const refs = planned.map(row => home.collection('occurrences')
      .doc(remoteOccurrenceId(choreId, row.scheduledEpochDay)));
    const snapshots = await tx.getAll(...refs);
    const missing = [];
    for (let n = 0; n < planned.length; n++) {
      const previous = snapshots[n], row = planned[n];
      if (previous.exists) {
        if (previous.data().definitionId !== choreId ||
            previous.data().scheduledEpochDay !== row.scheduledEpochDay) {
          throw new HttpsError('already-exists', 'Occurrence identity mismatch.');
        }
      } else {
        try { validateScheduledEpochDay(row.scheduledEpochDay); }
        catch { throw new HttpsError('invalid-argument', 'Outside the creation window.'); }
        missing.push({ ref: refs[n], row });
      }
    }
    // Validate all new assignees before any write. Revoked membership must not
    // produce a new occurrence, but previously completed records are retained.
    await Promise.all([...new Set(missing.map(entry => entry.row.assignedUid))]
      .map(member => checkAssignee(tx, home, member, house.data().ownerUid)));
    for (const { ref, row } of missing) {
      tx.create(ref, {
        definitionId: choreId, ...row,
        completed: false, completedByUid: null, completedAt: null, skippedAt: null,
        revision: 1, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
      });
    }
    return { created: missing.length, existing: planned.length - missing.length };
  });
});
