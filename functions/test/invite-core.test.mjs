import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  createInviteCode, normalizeInviteCode, inviteDigest, rateLimitNext,
  requireHouseholdId, INVITE_TTL_MS, MAX_HOUSEHOLD_MEMBERS,
  requireAvailableMemberSlot, requireRemovableMemberCount,
} from '../invite-core.js';

test('invite code is random-looking 64-bit uppercase hex with safe copy separators', () => {
  const first = createInviteCode();
  const second = createInviteCode();
  assert.match(first, /^[A-F0-9]{4}(-[A-F0-9]{4}){3}$/);
  assert.notEqual(first, second);
  assert.equal(normalizeInviteCode(first.toLowerCase()), first.replaceAll('-', ''));
  assert.equal(inviteDigest(first), inviteDigest(first.replaceAll('-', '')));
  assert.equal(inviteDigest(first).length, 64);
});
test('rejects short, injected and invalid codes', () => {
  for (const value of [null, '', '1234', '../abcdefgh', 'invalid-codes!!!']) {
    assert.throws(() => normalizeInviteCode(value));
  }
});
test('limits failed attempts to ten in fifteen minutes', () => {
  const now = 1234567890;
  let state;
  for (let i = 0; i < 10; i++) state = rateLimitNext(state, now);
  assert.equal(state.attempts, 10);
  assert.throws(() => rateLimitNext(state, now), /rate-exceeded/);
  assert.equal(rateLimitNext(state, now + 15 * 60 * 1000).attempts, 1);
});
test('cannot use path traversal or empty household ID', () => {
  for (const value of ['', null, '../p', 'a/b', '.', '..']) {
    assert.throws(() => requireHouseholdId(value));
  }
  assert.equal(requireHouseholdId('my-house'), 'my-house');
  assert.equal(INVITE_TTL_MS, 24 * 60 * 60 * 1000);
  assert.equal(MAX_HOUSEHOLD_MEMBERS, 8);
});

test('household capacity cannot become zero, negative or exceed eight', () => {
  for (let count = 1; count <= 7; count++) assert.equal(requireAvailableMemberSlot(count), count);
  for (const invalid of [0, -1, 8, 9, 1.5, null]) {
    assert.throws(() => requireAvailableMemberSlot(invalid));
  }
  for (let count = 2; count <= 8; count++) assert.equal(requireRemovableMemberCount(count), count);
  for (const invalid of [0, 1, 9, -1, null]) {
    assert.throws(() => requireRemovableMemberCount(invalid));
  }
});
