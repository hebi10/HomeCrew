import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { canonicalMigrationDigest, validateAtomicMigration, MAX_ATOMIC_MIGRATION_ROWS } from '../migration-core.js';
import { remoteOccurrenceId } from '../sync-core.js';

const day = 20782;
const id = createHash('sha256').update('homecrew-local-definition-v1:local-task').digest('hex');
function fixture() {
  const definition = {
    id, sourceLocalId: 'local-task', name: '설거지', category: '주방', description: '원본 설명',
    points: 3, assigneeUid: 'uid-a', scheduledTime: '19:20', repeatLabel: '매주',
    schedule: { startEpochDay: day, repeatMode: 'WEEKLY', weekdays: [1,3],
      intervalDays: 2, assignmentMode: 'ROUND_ROBIN', memberUids: ['uid-a','uid-b'] },
    sourceWeekdayMask: 5, sourceRepeatIntervalDays: 2,
    sourceCreatedAtEpochMillis: 1000, sourceUpdatedAtEpochMillis: 2000,
  };
  const occurrence = {
    id: remoteOccurrenceId(id, day - 1), sourceLocalId: 'local-occurrence', definitionId: id,
    scheduledEpochDay: day - 1, assignedUid: 'uid-b', workloadPoints: 3, sourceWorkloadPoints: 0,
    completed: true, completedByUid: 'uid-a', completedAtEpochMillis: 1500,
    skippedAtEpochMillis: null, skipReason: null,
  };
  return { version: 1, householdId: 'house', ownerUid: 'uid-a', definitions: [definition],
    occurrences: [occurrence], memberUids: ['uid-a','uid-b'], defaultMemberUid: 'uid-a', legacyWorkloadCount: 1 };
}

test('canonical manifest accepts all history fields and UTF-8 lengths', () => {
  const raw = fixture();
  assert.equal(validateAtomicMigration(raw, 'house', 'uid-a'), raw);
  assert.equal(canonicalMigrationDigest(raw), canonicalMigrationDigest(Object.fromEntries(Object.entries(raw).reverse())));
  const copy = structuredClone(raw);
  copy.occurrences[0].completedAtEpochMillis++;
  assert.notEqual(canonicalMigrationDigest(raw), canonicalMigrationDigest(copy));
  const expected = createHash('sha256').update('m2:s1:ai1;s1:bs3:한').digest('hex');
  assert.equal(canonicalMigrationDigest({ b: '한', a: 1 }), expected);
});

test('rejects forged identities, extra fields, incorrect mapping, damaged history and oversized requests', () => {
  for (const mutate of [
    raw => raw.ownerUid = 'other',
    raw => raw.definitions[0].id = 'forged',
    raw => raw.occurrences[0].definitionId = 'missing',
    raw => raw.occurrences[0].completedByUid = 'other',
    raw => raw.occurrences[0].completed = false,
    raw => raw.occurrences[0].workloadPoints = 1,
    raw => raw.definitions[0].schedule.weekdays = [2],
    raw => raw.occurrences[0].skippedAtEpochMillis = 123,
    raw => raw.definitions[0].extra = 'unexpected',
    raw => raw.legacyWorkloadCount = 0,
    raw => raw.occurrences = Array(MAX_ATOMIC_MIGRATION_ROWS).fill(raw.occurrences[0]),
    raw => raw.memberUids = ['uid-a','uid-a'],
  ]) {
    const raw = fixture();
    mutate(raw);
    assert.throws(() => validateAtomicMigration(raw, 'house', 'uid-a'));
  }
});
