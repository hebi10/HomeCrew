import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeRemoteSchedule } from '../recurrence-core.js';
import { planFutureSchedule, futureScheduleDigest } from '../future-schedule-core.js';

const today = Math.floor(Date.UTC(2026, 10, 25) / 86400000);
const schedule = normalizeRemoteSchedule({
  startEpochDay: today, repeatMode: 'CUSTOM', intervalDays: 2,
  assignmentMode: 'FIXED', memberUids: ['bob'],
});
const row = (offset, extra = {}) => ({
  id: 'occ-' + offset, scheduledEpochDay: today + offset,
  assignedUid: 'alice', revision: 1, completed: false, ...extra,
});

test('only untouched future records may cancel, reassign or restore; no history deletion', () => {
  const plan = planFutureSchedule(schedule, [
    row(0), row(-1), row(1), row(2), row(3, { revision: 3 }),
    row(4, { revision: 2, completed: true }),
    row(6, { revision: 2, scheduleAdjustmentRevision: 2, scheduleCancelledAt: 123 }),
    row(63),
  ], today);
  assert.deepEqual(plan.changes.map(c => [c.date - today, c.type]), [[1,'CANCEL'],[2,'REASSIGN'],[6,'RESTORE']]);
  assert.equal(plan.protectedCount, 2);
  assert.equal(plan.restored, 1);
  assert.notEqual(futureScheduleDigest({ today }, plan), futureScheduleDigest({ today: today + 1 }, plan));
});

test('balanced rule never invents reassignment or restoration', () => {
  const balanced = normalizeRemoteSchedule({ ...schedule, assignmentMode: 'BALANCED' });
  const plan = planFutureSchedule(balanced, [
    row(1), row(2), row(4, { scheduleCancelledAt: 1, revision: 2, scheduleAdjustmentRevision: 2 }),
  ], today);
  assert.equal(plan.cancelled, 1);
  assert.equal(plan.reassigned, 0);
  assert.equal(plan.restored, 0);
  assert.equal(plan.balancedDeferred, 2);
});
