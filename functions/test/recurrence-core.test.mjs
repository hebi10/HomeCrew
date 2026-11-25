import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeRemoteSchedule, isScheduledDate, scheduledAssignee, scheduleWindow } from '../recurrence-core.js';

const day = (y, m, d) => Date.UTC(y, m - 1, d) / 86400000;
const start = day(2026, 11, 25); // Wednesday
const rule = (repeatMode, extra = {}) => normalizeRemoteSchedule({
  startEpochDay: start, repeatMode, assignmentMode: 'FIXED', memberUids: ['alice'], ...extra,
});

test('all six repeat modes use local planner date semantics', () => {
  assert.deepEqual(scheduleWindow(rule('NONE'), start - 1, start + 5).map(r => r.scheduledEpochDay), [start]);
  assert.equal(scheduleWindow(rule('DAILY'), start, start + 62).length, 63);
  assert.deepEqual(scheduleWindow(rule('CUSTOM', { intervalDays: 3 }), start, start + 8).map(r => r.scheduledEpochDay),
    [start, start + 3, start + 6]);
  assert.deepEqual(scheduleWindow(rule('WEEKLY', { weekdays: [1, 3] }), start, start + 14).map(r => r.scheduledEpochDay),
    [start, start + 5, start + 7, start + 12, start + 14]);
  assert.deepEqual(scheduleWindow(rule('BIWEEKLY', { weekdays: [1, 3] }), start, start + 14).map(r => r.scheduledEpochDay),
    [start, start + 12, start + 14]);
  const monthly = rule('MONTHLY', { startEpochDay: day(2024, 1, 31) });
  assert.equal(isScheduledDate(monthly, day(2024, 2, 29)), true);
  assert.equal(isScheduledDate(monthly, day(2024, 3, 31)), true);
  assert.equal(isScheduledDate(monthly, day(2024, 3, 29)), false);
});

test('rotation is anchored at start, independent of requested window and date gaps', () => {
  const schedule = rule('BIWEEKLY', {
    weekdays: [1, 3], assignmentMode: 'ROUND_ROBIN', memberUids: ['alice', 'bob'],
  });
  assert.equal(scheduledAssignee(schedule, start), 'alice');
  assert.equal(scheduledAssignee(schedule, start + 12), 'bob');
  assert.equal(scheduledAssignee(schedule, start + 14), 'alice');
  assert.equal(scheduleWindow(schedule, start + 12, start + 14)[0].assignedUid, 'bob');
  assert.throws(() => scheduledAssignee(schedule, start + 7));
  const daily = rule('DAILY', { startEpochDay: day(2000, 1, 1), assignmentMode: 'ROUND_ROBIN', memberUids: ['a', 'b', 'c'] });
  const far = day(2026, 12, 1);
  assert.equal(scheduledAssignee(daily, far), ['a', 'b', 'c'][(far - daily.startEpochDay) % 3]);
});

test('rejects invalid rules, duplicate assignees, unsupported balancing and excessive windows', () => {
  for (const invalid of [
    { startEpochDay: NaN }, { assignmentMode: 'BALANCED' }, { memberUids: [] },
    { memberUids: ['alice', 'alice'], assignmentMode: 'ROUND_ROBIN' },
    { memberUids: ['../alice'] }, { repeatMode: 'WEEKLY', weekdays: [] },
    { repeatMode: 'CUSTOM', intervalDays: 31 }, { weekdays: [0] },
  ]) assert.throws(() => rule('DAILY', invalid));
  assert.throws(() => scheduleWindow(rule('DAILY'), start, start + 63));
  assert.throws(() => scheduleWindow(rule('DAILY'), start, start - 1));
});
