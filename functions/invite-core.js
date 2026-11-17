import { createHash, randomBytes } from 'node:crypto';

export const INVITE_TTL_MS = 24 * 60 * 60 * 1000;
export const RATE_WINDOW_MS = 15 * 60 * 1000;
export const MAX_ATTEMPTS = 10;
export const MAX_HOUSEHOLD_MEMBERS = 8;

export function createInviteCode() {
  const raw = randomBytes(8).toString('hex').toUpperCase();
  return raw.match(/.{1,4}/g).join('-');
}

export function normalizeInviteCode(value) {
  if (typeof value !== 'string') throw new Error('invalid-invite-code');
  const code = value.trim().toUpperCase().replaceAll('-', '');
  if (!/^[0-9A-F]{16}$/.test(code)) throw new Error('invalid-invite-code');
  return code;
}

export function inviteDigest(normalizedCode) {
  return createHash('sha256').update('homecrew-invite-v1:' + normalizeInviteCode(normalizedCode)).digest('hex');
}

export function rateLimitNext(previous, nowEpochMillis, maxAttempts = MAX_ATTEMPTS) {
  const start = Number(previous?.windowStartMs ?? 0);
  const count = Number(previous?.attempts ?? 0);
  const current = start > 0 && nowEpochMillis - start < RATE_WINDOW_MS
    ? { windowStartMs: start, attempts: count + 1 }
    : { windowStartMs: nowEpochMillis, attempts: 1 };
  if (current.attempts > maxAttempts) throw new Error('rate-exceeded');
  return current;
}

export function requireHouseholdId(value) {
  if (typeof value !== 'string' || value.length < 1 || value.length > 128 ||
    value === '.' || value === '..' || value.includes('/')) {
    throw new Error('invalid-household');
  }
  return value;
}

export function requireAvailableMemberSlot(value) {
  if (!Number.isInteger(value) || value < 1 || value >= MAX_HOUSEHOLD_MEMBERS) {
    throw new Error('household-capacity-exceeded');
  }
  return value;
}

export function requireRemovableMemberCount(value) {
  if (!Number.isInteger(value) || value <= 1 || value > MAX_HOUSEHOLD_MEMBERS) {
    throw new Error('invalid-member-count');
  }
  return value;
}
