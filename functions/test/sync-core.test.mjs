import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  normalizeRemoteChore, nextRevision, mutationDigest, requireDocumentId,
  remoteOccurrenceId, validateScheduledEpochDay,
} from '../sync-core.js';

const task = { id: 'chore-123', name: ' 설거지 ', category: '주방', points: 2, description: '',
  assigneeUid: 'alice-uid' };

test('chore input is trimmed and bounded before server writes', () => {
  assert.equal(normalizeRemoteChore(task).name, '설거지');
  assert.throws(() => normalizeRemoteChore({ ...task, points: -1 }));
  assert.throws(() => normalizeRemoteChore({ ...task, assigneeUid: '../other' }));
  assert.throws(() => normalizeRemoteChore({ ...task, name: 'x'.repeat(121) }));
});

test('multi-device edits use expected revision and never overwrite stale changes', () => {
  assert.equal(nextRevision(4, 4), 5);
  assert.throws(() => nextRevision(5, 4), /revision-conflict/);
  assert.throws(() => nextRevision(0, 0));
});

test('completion retries retain stable operation hashes and detect different payloads', () => {
  const args = ['house-1', 'alice', '12345678-1234-1234-1234-123456789abc', 'occ-1'];
  const a = mutationDigest(...args, true);
  assert.equal(a, mutationDigest(...args, true));
  assert.notEqual(a, mutationDigest(...args, false));
  assert.throws(() => mutationDigest('home', 'u', 'short', 'occ-1', true));
});

test('path segments cannot escape parent collection', () => {
  for (const value of ['', '.', '..', '../../x', 'a/b']) {
    assert.throws(() => requireDocumentId(value));
  }
});

test('one occurrence per task per day has a stable non-forgeable document path', () => {
  const a = remoteOccurrenceId('chore-123', 20774);
  assert.match(a, /^[a-f0-9]{64}$/);
  assert.equal(remoteOccurrenceId('chore-123', 20774), a);
  assert.notEqual(remoteOccurrenceId('chore-123', 20775), a);
  assert.notEqual(remoteOccurrenceId('chore-124', 20774), a);
  assert.throws(() => remoteOccurrenceId('../other', 20774));
});

test('server day bound rejects stale and arbitrary future occurrence generation', () => {
  // 2026-11-17 at 00:00 UTC, 2026-11-17 in Seoul.
  const now = Date.UTC(2026, 10, 17, 0);
  const today = Math.floor((now + 9 * 3600000) / 86400000);
  assert.equal(validateScheduledEpochDay(today, now), today);
  assert.equal(validateScheduledEpochDay(today + 62, now), today + 62);
  for (const day of [today - 1, today + 63, 1.5, NaN]) {
    assert.throws(() => validateScheduledEpochDay(day, now));
  }
});
