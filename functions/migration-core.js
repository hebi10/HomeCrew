import { createHash } from 'node:crypto';
import { normalizeRemoteSchedule } from './recurrence-core.js';
import { requireDocumentId, remoteOccurrenceId } from './sync-core.js';

export const MAX_ATOMIC_MIGRATION_ROWS = 300;
export const MAX_ATOMIC_MIGRATION_BYTES = 512 * 1024;
const MIN_DAY = Date.UTC(1900, 0, 1) / 86400000;
const MAX_DAY = Date.UTC(2200, 11, 31) / 86400000;
const MAX_MS = 253402300799999;
const sha = value => createHash('sha256').update(value, 'utf8').digest('hex');

export function canonicalMigrationDigest(value) {
  const hash = createHash('sha256');
  const add = item => {
    if (item === null) hash.update('n;');
    else if (typeof item === 'boolean') hash.update(item ? 'b1;' : 'b0;');
    else if (Number.isSafeInteger(item)) hash.update('i' + item + ';');
    else if (typeof item === 'string') {
      hash.update('s' + Buffer.byteLength(item, 'utf8') + ':');
      hash.update(item, 'utf8');
    } else if (Array.isArray(item)) {
      hash.update('l' + item.length + ':');
      item.forEach(add);
    } else if (item && typeof item === 'object') {
      const keys = Object.keys(item).sort();
      hash.update('m' + keys.length + ':');
      for (const key of keys) { add(key); add(item[key]); }
    } else throw new Error('invalid-digest-value');
  };
  add(value);
  return hash.digest('hex');
}

function exactKeys(value, fields) {
  if (!value || typeof value !== 'object' || Array.isArray(value) ||
      Object.keys(value).length !== fields.length ||
      fields.some(field => !Object.hasOwn(value, field))) throw new Error('invalid-fields');
}
function assert(condition) { if (!condition) throw new Error('invalid-manifest'); }
function text(value, limit, nonblank = false) {
  assert(typeof value === 'string' && value.length <= limit && (!nonblank || value.trim().length > 0));
  // The Kotlin and JS canonical digests must encode the same Unicode scalar values.
  assert(Buffer.from(value, 'utf8').toString('utf8') === value);
}
function integer(value, min, max) { assert(Number.isSafeInteger(value) && value >= min && value <= max); }
function timestamp(value) { integer(value, 0, MAX_MS); }
function optionalTimestamp(value) { if (value !== null) timestamp(value); }
function uid(value) {
  text(value, 128, true);
  assert(value !== '.' && value !== '..' && !value.includes('/'));
}

const rootKeys = ['version','householdId','ownerUid','definitions','occurrences','memberUids','defaultMemberUid','legacyWorkloadCount'];
const definitionKeys = ['id','sourceLocalId','name','category','description','points','assigneeUid',
  'scheduledTime','repeatLabel','schedule','sourceWeekdayMask','sourceRepeatIntervalDays',
  'sourceCreatedAtEpochMillis','sourceUpdatedAtEpochMillis'];
const occurrenceKeys = ['id','sourceLocalId','definitionId','scheduledEpochDay','assignedUid',
  'workloadPoints','sourceWorkloadPoints','completed','completedByUid','completedAtEpochMillis',
  'skippedAtEpochMillis','skipReason'];
const scheduleKeys = ['startEpochDay','repeatMode','weekdays','intervalDays','assignmentMode','memberUids'];

export const MAX_STAGED_MIGRATION_ROWS = 102000;
export const MAX_STAGED_MIGRATION_BYTES = 16 * 1024 * 1024;

function validateMigration(raw, expectedHouseholdId, expectedOwnerUid, maxRows, maxBytes) {
  exactKeys(raw, rootKeys);
  assert(raw.version === 1 && raw.householdId === expectedHouseholdId && raw.ownerUid === expectedOwnerUid);
  requireDocumentId(raw.householdId);
  uid(raw.ownerUid);
  assert(Array.isArray(raw.definitions) && Array.isArray(raw.occurrences) &&
    raw.definitions.length > 0 && raw.definitions.length <= 2000 &&
    raw.occurrences.length <= 100000 && raw.definitions.length + raw.occurrences.length <= maxRows);
  assert(Buffer.byteLength(JSON.stringify(raw), 'utf8') <= maxBytes);
  assert(Array.isArray(raw.memberUids) && raw.memberUids.length >= 1 && raw.memberUids.length <= 8);
  raw.memberUids.forEach(uid);
  const members = new Set(raw.memberUids);
  assert(members.size === raw.memberUids.length && members.has(raw.defaultMemberUid));
  const definitions = new Map();
  for (const def of raw.definitions) {
    exactKeys(def, definitionKeys);
    text(def.sourceLocalId, 512, true);
    assert(def.id === sha('homecrew-local-definition-v1:' + def.sourceLocalId) && !definitions.has(def.id));
    text(def.name, 40, true); text(def.category, 64, true);
    text(def.description, 300); text(def.repeatLabel, 128);
    integer(def.points, 1, 5);
    assert(members.has(def.assigneeUid));
    assert(typeof def.scheduledTime === 'string' && /^([01][0-9]|2[0-3]):[0-5][0-9]$/.test(def.scheduledTime));
    integer(def.sourceWeekdayMask, 0, 127);
    integer(def.sourceRepeatIntervalDays, 1, 30);
    timestamp(def.sourceCreatedAtEpochMillis);
    timestamp(def.sourceUpdatedAtEpochMillis);
    assert(def.sourceUpdatedAtEpochMillis >= def.sourceCreatedAtEpochMillis);
    exactKeys(def.schedule, scheduleKeys);
    const rule = normalizeRemoteSchedule(def.schedule);
    assert(canonicalMigrationDigest(rule) === canonicalMigrationDigest(def.schedule));
    assert(rule.memberUids.every(member => members.has(member)) && rule.memberUids.includes(def.assigneeUid));
    const days = Array.from({length: 7}, (_, i) => i + 1)
      .filter(day => (def.sourceWeekdayMask & (1 << (day - 1))) !== 0);
    assert(rule.repeatMode !== 'WEEKLY' && rule.repeatMode !== 'BIWEEKLY' ||
      JSON.stringify(days) === JSON.stringify(rule.weekdays));
    assert(rule.repeatMode !== 'CUSTOM' || rule.intervalDays === def.sourceRepeatIntervalDays);
    definitions.set(def.id, def);
  }
  const ids = new Set(), localIds = new Set();
  let legacy = 0;
  for (const row of raw.occurrences) {
    exactKeys(row, occurrenceKeys);
    text(row.sourceLocalId, 1024, true);
    integer(row.scheduledEpochDay, MIN_DAY, MAX_DAY);
    const definition = definitions.get(row.definitionId);
    assert(definition && row.id === remoteOccurrenceId(row.definitionId, row.scheduledEpochDay));
    assert(!ids.has(row.id) && !localIds.has(row.sourceLocalId));
    ids.add(row.id); localIds.add(row.sourceLocalId);
    assert(members.has(row.assignedUid));
    integer(row.workloadPoints, 1, 5); integer(row.sourceWorkloadPoints, 0, 5);
    assert(row.workloadPoints === (row.sourceWorkloadPoints || definition.points));
    if (row.sourceWorkloadPoints === 0) legacy++;
    assert(typeof row.completed === 'boolean');
    assert(row.completedByUid === null || members.has(row.completedByUid));
    optionalTimestamp(row.completedAtEpochMillis);
    optionalTimestamp(row.skippedAtEpochMillis);
    if (row.skipReason !== null) text(row.skipReason, 120);
    assert(!(row.completed && row.skippedAtEpochMillis !== null));
    assert(row.skippedAtEpochMillis !== null || row.skipReason === null);
    assert(row.completed || (row.completedByUid === null && row.completedAtEpochMillis === null));
  }
  assert(raw.legacyWorkloadCount === legacy);
  return raw;
}

export function validateAtomicMigration(raw, householdId, ownerUid) {
  return validateMigration(raw, householdId, ownerUid, MAX_ATOMIC_MIGRATION_ROWS, MAX_ATOMIC_MIGRATION_BYTES);
}

export function validateStagedMigration(raw, householdId, ownerUid) {
  return validateMigration(raw, householdId, ownerUid, MAX_STAGED_MIGRATION_ROWS, MAX_STAGED_MIGRATION_BYTES);
}

// Reject unknown fields and non-canonical scalar values before accepting a
// chunk. Referential integrity and full semantic validation occur at seal.
export function validateMigrationChunkRows(definitions, occurrences) {
  assert(Array.isArray(definitions) && Array.isArray(occurrences));
  for (const def of definitions) {
    exactKeys(def, definitionKeys);
    exactKeys(def.schedule, scheduleKeys);
  }
  for (const row of occurrences) exactKeys(row, occurrenceKeys);
  canonicalMigrationDigest({ definitions, occurrences });
}
