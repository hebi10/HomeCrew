import { createHash } from 'node:crypto';

export function normalizeSharedHouseholdName(value) {
  if (typeof value !== 'string') throw new Error('invalid-name');
  const name = value.trim();
  if (!name || name.length > 80) throw new Error('invalid-name');
  return name;
}

export function sharedHouseholdId(uid, requestId) {
  if (typeof uid !== 'string' || !uid || uid.length > 128 || uid.includes('/')) {
    throw new Error('invalid-uid');
  }
  if (typeof requestId !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(requestId)) {
    throw new Error('invalid-request');
  }
  return createHash('sha256').update(JSON.stringify([uid, requestId])).digest('hex');
}
