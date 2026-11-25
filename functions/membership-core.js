import { createHash } from 'node:crypto';

export function validMemberUid(value) {
  return typeof value === 'string' && value.trim().length > 0 &&
    value.length <= 128 && value !== '.' && value !== '..' && !value.includes('/');
}

export function normalizeMemberDisplayName(value) {
  if (typeof value !== 'string') throw new Error('invalid-display-name');
  const name = value.normalize('NFC').trim();
  if (!name || name.length > 12 || /[\p{Cc}\p{Cf}\p{Cs}]/u.test(name)) throw new Error('invalid-display-name');
  return name;
}

export function ownershipRevision(data) {
  const value = data.ownershipRevision ?? 0;
  if (!Number.isSafeInteger(value) || value < 0 || value >= Number.MAX_SAFE_INTEGER) {
    throw new Error('invalid-ownership-revision');
  }
  return value;
}

export function memberRosterDigest(householdId, ownerUid, revision, people, transferBlocked) {
  return createHash('sha256').update(JSON.stringify({
    householdId, ownerUid, revision, transferBlocked,
    people: [...people].sort((a,b) => a.uid < b.uid ? -1 : a.uid > b.uid ? 1 : 0).map(person => [
      person.uid, person.isOwner, person.joinedIdentity, person.displayName, person.profileRevision,
    ]),
  })).digest('hex');
}

export function assertDistinctMemberName(people, uid, displayName) {
  const key = displayName.toLowerCase();
  if (people.some(person => person.uid !== uid && person.displayName?.toLowerCase() === key)) {
    throw new Error('duplicate-display-name');
  }
}
