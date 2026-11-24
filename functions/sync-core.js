import { createHash } from 'node:crypto';

export function requireDocumentId(value) {
  if (typeof value !== 'string' || !/^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$/.test(value) ||
    value === '.' || value === '..') throw new Error('invalid-document-id');
  return value;
}

export function normalizeRemoteChore(raw) {
  if (!raw || typeof raw !== 'object') throw new Error('invalid-chore');
  const id = requireDocumentId(raw.id);
  const name = typeof raw.name === 'string' ? raw.name.trim() : '';
  const category = typeof raw.category === 'string' ? raw.category.trim() : '';
  const description = typeof raw.description === 'string' ? raw.description.trim() : '';
  const assigneeUid = typeof raw.assigneeUid === 'string' ? raw.assigneeUid : '';
  const points = raw.points;
  if (!name || name.length > 120 || category.length > 64 || description.length > 2000 ||
    !Number.isInteger(points) || points < 1 || points > 5 || !assigneeUid ||
    assigneeUid.length > 128 || assigneeUid.includes('/')) throw new Error('invalid-chore');
  return { id, name, category, description, points, assigneeUid };
}

export function nextRevision(current, expected) {
  if (!Number.isSafeInteger(current) || current < 1 ||
    !Number.isSafeInteger(expected) || expected < 1) throw new Error('invalid-revision');
  if (current !== expected) throw new Error('revision-conflict');
  return current + 1;
}

export function mutationDigest(householdId, uid, requestId, targetId, completed) {
  if (typeof requestId !== 'string' || !/^[a-zA-Z0-9_-]{20,72}$/.test(requestId)) {
    throw new Error('invalid-mutation-id');
  }
  return createHash('sha256').update(JSON.stringify({
    householdId, uid, requestId, targetId, completed,
  })).digest('hex');
}

export function remoteOccurrenceId(choreId, scheduledEpochDay) {
  requireDocumentId(choreId);
  if (!Number.isSafeInteger(scheduledEpochDay)) throw new Error('invalid-occurrence-date');
  return createHash('sha256')
    .update('homecrew-occurrence-v1:' + choreId + ':' + scheduledEpochDay)
    .digest('hex');
}

export function validateScheduledEpochDay(epochDay, nowEpochMillis = Date.now()) {
  if (!Number.isSafeInteger(epochDay) || !Number.isFinite(nowEpochMillis)) {
    throw new Error('invalid-occurrence-date');
  }
  // The household's first release uses Asia/Seoul for its daily schedule.
  const today = Math.floor((nowEpochMillis + 9 * 60 * 60 * 1000) / 86400000);
  if (epochDay < today || epochDay > today + 62) {
    throw new Error('outside-planning-horizon');
  }
  return epochDay;
}
