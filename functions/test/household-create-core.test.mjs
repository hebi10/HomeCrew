import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeSharedHouseholdName, sharedHouseholdId } from '../household-create-core.js';

const key = '6dfdfbc0-3cc0-4db9-8cb3-1eb24cfc9c10';

test('space creation validates and trims names', () => {
  assert.equal(normalizeSharedHouseholdName(' 우리 집 '), '우리 집');
  for (const name of ['', '  ', 'a'.repeat(81), null, {}]) {
    assert.throws(() => normalizeSharedHouseholdName(name));
  }
});

test('retry uses the same household ID; accounts and new requests are isolated', () => {
  const a = sharedHouseholdId('uid-a', key);
  assert.match(a, /^[a-f0-9]{64}$/);
  assert.equal(a, sharedHouseholdId('uid-a', key));
  assert.notEqual(a, sharedHouseholdId('uid-b', key));
  assert.notEqual(a, sharedHouseholdId('uid-a', '6dfdfbc0-3cc0-4db9-8cb3-1eb24cfc9c11'));
  for (const bad of ['../../x', '', 'no-uuid', null]) {
    assert.throws(() => sharedHouseholdId('uid-a', bad));
  }
});
