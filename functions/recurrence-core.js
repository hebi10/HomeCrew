const DAY_MS = 86400000;
const MIN_DAY = Math.floor(Date.UTC(1900, 0, 1) / DAY_MS);
const MAX_DAY = Math.floor(Date.UTC(2200, 11, 31) / DAY_MS);
const MODES = new Set(['NONE', 'DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'CUSTOM']);

function day(value) {
  if (!Number.isSafeInteger(value) || value < MIN_DAY || value > MAX_DAY) {
    throw new Error('invalid-epoch-day');
  }
  return value;
}

function weekday(epochDay) {
  return ((epochDay + 3) % 7 + 7) % 7 + 1;
}

function monday(epochDay) {
  return epochDay - weekday(epochDay) + 1;
}

function validUid(uid) {
  return typeof uid === 'string' && uid.length > 0 && uid.length <= 128 &&
    uid !== '.' && uid !== '..' && !uid.includes('/');
}

export function normalizeRemoteSchedule(raw) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('invalid-schedule');
  const startEpochDay = day(raw.startEpochDay);
  const repeatMode = raw.repeatMode;
  const assignmentMode = raw.assignmentMode;
  if (!MODES.has(repeatMode)) throw new Error('invalid-repeat-mode');
  // Balanced assignment requires a shared workload ledger; do not silently
  // approximate it or treat it as round robin.
  if (!['FIXED', 'ROUND_ROBIN'].includes(assignmentMode)) throw new Error('unsupported-assignment');
  const memberUids = raw.memberUids;
  if (!Array.isArray(memberUids) || memberUids.length < 1 || memberUids.length > 8 ||
      !memberUids.every(validUid) || new Set(memberUids).size !== memberUids.length ||
      (assignmentMode === 'FIXED' && memberUids.length !== 1)) throw new Error('invalid-members');
  const suppliedDays = raw.weekdays ?? [];
  if (!Array.isArray(suppliedDays) || suppliedDays.some(n => !Number.isInteger(n) || n < 1 || n > 7) ||
      new Set(suppliedDays).size !== suppliedDays.length) throw new Error('invalid-weekdays');
  const usesWeekdays = repeatMode === 'WEEKLY' || repeatMode === 'BIWEEKLY';
  if (usesWeekdays && suppliedDays.length === 0) throw new Error('missing-weekdays');
  const interval = raw.intervalDays ?? 2;
  if (!Number.isInteger(interval) || interval < 1 || interval > 30) throw new Error('invalid-interval');
  return {
    startEpochDay, repeatMode,
    weekdays: usesWeekdays ? [...suppliedDays].sort((a, b) => a - b) : [],
    intervalDays: repeatMode === 'CUSTOM' ? interval : 2,
    assignmentMode, memberUids: [...memberUids],
  };
}

function utcDate(epochDay) { return new Date(epochDay * DAY_MS); }

export function isScheduledDate(schedule, epochDay) {
  day(epochDay);
  const start = schedule.startEpochDay;
  if (epochDay < start) return false;
  const elapsed = epochDay - start;
  switch (schedule.repeatMode) {
    case 'NONE': return elapsed === 0;
    case 'DAILY': return true;
    case 'WEEKLY': return schedule.weekdays.includes(weekday(epochDay));
    case 'BIWEEKLY': return schedule.weekdays.includes(weekday(epochDay)) &&
      Math.floor((monday(epochDay) - monday(start)) / 7) % 2 === 0;
    case 'CUSTOM': return elapsed % schedule.intervalDays === 0;
    case 'MONTHLY': {
      const origin = utcDate(start);
      const current = utcDate(epochDay);
      const monthEnd = new Date(Date.UTC(current.getUTCFullYear(), current.getUTCMonth() + 1, 0));
      return current.getUTCDate() === Math.min(origin.getUTCDate(), monthEnd.getUTCDate());
    }
    default: throw new Error('invalid-repeat-mode');
  }
}

function scheduledOrdinal(schedule, epochDay) {
  const start = schedule.startEpochDay;
  const elapsed = epochDay - start;
  switch (schedule.repeatMode) {
    case 'NONE': return 0;
    case 'DAILY': return elapsed;
    case 'CUSTOM': return Math.floor(elapsed / schedule.intervalDays);
    case 'MONTHLY': {
      const a = utcDate(start), b = utcDate(epochDay);
      return (b.getUTCFullYear() - a.getUTCFullYear()) * 12 + b.getUTCMonth() - a.getUTCMonth();
    }
    case 'WEEKLY':
    case 'BIWEEKLY': {
      const anchor = monday(start);
      const cycle = schedule.repeatMode === 'WEEKLY' ? 7 : 14;
      const before = bound => {
        const offset = bound - anchor;
        const remainder = offset % cycle;
        return Math.floor(offset / cycle) * schedule.weekdays.length +
          schedule.weekdays.filter(w => w - 1 < remainder).length;
      };
      return before(epochDay) - before(start);
    }
    default: throw new Error('invalid-repeat-mode');
  }
}

export function scheduledAssignee(schedule, epochDay) {
  if (!isScheduledDate(schedule, epochDay)) throw new Error('not-a-scheduled-date');
  return schedule.assignmentMode === 'FIXED' ? schedule.memberUids[0] :
    schedule.memberUids[scheduledOrdinal(schedule, epochDay) % schedule.memberUids.length];
}

export function scheduleWindow(schedule, fromEpochDay, toEpochDay) {
  day(fromEpochDay); day(toEpochDay);
  if (toEpochDay < fromEpochDay || toEpochDay - fromEpochDay > 62) throw new Error('invalid-window');
  const result = [];
  for (let epochDay = fromEpochDay; epochDay <= toEpochDay; epochDay++) {
    if (isScheduledDate(schedule, epochDay)) {
      result.push({ scheduledEpochDay: epochDay, assignedUid: scheduledAssignee(schedule, epochDay) });
    }
  }
  return result;
}
