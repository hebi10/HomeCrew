import { createHash } from 'node:crypto';
import { isScheduledDate, scheduledAssignee } from './recurrence-core.js';

export function planFutureSchedule(schedule, rows, today) {
  const changes = [];
  let protectedCount = 0;
  let balancedDeferred = 0;
  for (const row of [...rows].sort((a, b) => a.scheduledEpochDay - b.scheduledEpochDay)) {
    const date = row.scheduledEpochDay;
    if (date <= today || date > today + 62) continue;
    const untouched = row.revision === 1 || row.scheduleAdjustmentRevision === row.revision;
    if (!untouched || row.completed !== false || row.completedAt != null ||
        row.completedByUid != null || row.skippedAt != null) {
      protectedCount++;
      continue;
    }
    const cancelled = row.scheduleCancelledAt != null;
    const scheduled = isScheduledDate(schedule, date);
    if (!scheduled) {
      if (!cancelled) changes.push({ id: row.id, date, revision: row.revision, type: 'CANCEL' });
      continue;
    }
    if (schedule.assignmentMode === 'BALANCED') {
      // Do not rebalance previously assigned work or guess a workload for restored rows.
      if (cancelled || !schedule.memberUids.includes(row.assignedUid)) balancedDeferred++;
      continue;
    }
    const assignedUid = scheduledAssignee(schedule, date);
    if (cancelled || assignedUid !== row.assignedUid) {
      changes.push({ id: row.id, date, revision: row.revision,
        type: cancelled ? 'RESTORE' : 'REASSIGN', assignedUid });
    }
  }
  return {
    changes, protectedCount, balancedDeferred,
    cancelled: changes.filter(c => c.type === 'CANCEL').length,
    reassigned: changes.filter(c => c.type === 'REASSIGN').length,
    restored: changes.filter(c => c.type === 'RESTORE').length,
  };
}

export function futureScheduleDigest(scope, plan) {
  return createHash('sha256').update(JSON.stringify({ scope, plan })).digest('hex');
}
