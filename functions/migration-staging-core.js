import { canonicalMigrationDigest, validateMigrationChunkRows,
  validateStagedMigration, MAX_STAGED_MIGRATION_BYTES, MAX_STAGED_MIGRATION_ROWS } from './migration-core.js';
import { requireDocumentId } from './sync-core.js';

export const MAX_STAGE_CHUNKS = 2048;
export const MAX_CHUNK_ROWS = 100;
export const MAX_CHUNK_BYTES = 128 * 1024;
export const MAX_STAGE_BYTES = MAX_STAGED_MIGRATION_BYTES;
const fail = () => { throw new Error('invalid-stage-data'); };
const integer = (value, min, max) => {
  if (!Number.isSafeInteger(value) || value < min || value > max) fail();
};
export const validStageDigest = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
export const validSessionId = value => typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value);
const validUid = value => typeof value === 'string' && value.trim().length > 0 &&
  value.length <= 128 && value !== '.' && value !== '..' && !value.includes('/');

function fields(raw, expected) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw) ||
      Object.keys(raw).length !== expected.length || expected.some(k => !Object.hasOwn(raw, k))) fail();
}

export function stageHeader(raw, householdId, ownerUid) {
  fields(raw, ['version','householdId','ownerUid','digest','definitionCount','occurrenceCount',
    'memberUids','defaultMemberUid','legacyWorkloadCount','chunkCount']);
  requireDocumentId(householdId);
  if (raw.version !== 1 || raw.householdId !== householdId || raw.ownerUid !== ownerUid ||
      !validUid(ownerUid) || !validStageDigest(raw.digest)) fail();
  integer(raw.definitionCount, 1, 2000);
  integer(raw.occurrenceCount, 0, 100000);
  integer(raw.definitionCount + raw.occurrenceCount, 1, MAX_STAGED_MIGRATION_ROWS);
  integer(raw.legacyWorkloadCount, 0, raw.occurrenceCount);
  integer(raw.chunkCount, 1, MAX_STAGE_CHUNKS);
  if (raw.chunkCount > raw.definitionCount + raw.occurrenceCount ||
      raw.chunkCount * MAX_CHUNK_ROWS < raw.definitionCount + raw.occurrenceCount) fail();
  if (!Array.isArray(raw.memberUids) || raw.memberUids.length < 1 || raw.memberUids.length > 8 ||
      !raw.memberUids.every(validUid) || new Set(raw.memberUids).size !== raw.memberUids.length ||
      !raw.memberUids.includes(raw.defaultMemberUid)) fail();
  return raw;
}

export function stageChunk(raw) {
  fields(raw, ['index','definitions','occurrences']);
  integer(raw.index, 0, MAX_STAGE_CHUNKS - 1);
  validateMigrationChunkRows(raw.definitions, raw.occurrences);
  integer(raw.definitions.length + raw.occurrences.length, 1, MAX_CHUNK_ROWS);
  const payloadJson = JSON.stringify(raw);
  const bytes = Buffer.byteLength(payloadJson, 'utf8');
  integer(bytes, 1, MAX_CHUNK_BYTES);
  return { payloadJson, bytes, digest: canonicalMigrationDigest(raw),
    definitions: raw.definitions.length, occurrences: raw.occurrences.length };
}

export function appendStageCounts(header, progress, chunk) {
  const result = {
    nextIndex: progress.nextIndex + 1,
    receivedDefinitions: progress.receivedDefinitions + chunk.definitions,
    receivedOccurrences: progress.receivedOccurrences + chunk.occurrences,
    receivedBytes: progress.receivedBytes + chunk.bytes,
  };
  if (result.nextIndex > header.chunkCount || result.receivedDefinitions > header.definitionCount ||
      result.receivedOccurrences > header.occurrenceCount || result.receivedBytes > MAX_STAGE_BYTES) fail();
  if (result.nextIndex === header.chunkCount &&
      (result.receivedDefinitions !== header.definitionCount ||
       result.receivedOccurrences !== header.occurrenceCount)) fail();
  return result;
}

export function assembleStagedManifest(header, chunks) {
  if (chunks.length !== header.chunkCount) fail();
  const definitions = [], occurrences = [];
  let bytes = 0;
  for (let index = 0; index < chunks.length; index++) {
    const chunk = chunks[index];
    if (chunk.index !== index) fail();
    const normalized = stageChunk(chunk);
    bytes += normalized.bytes;
    if (bytes > MAX_STAGE_BYTES) fail();
    definitions.push(...chunk.definitions);
    occurrences.push(...chunk.occurrences);
    if (definitions.length > header.definitionCount || occurrences.length > header.occurrenceCount) fail();
  }
  if (definitions.length !== header.definitionCount || occurrences.length !== header.occurrenceCount) fail();
  const manifest = {
    version: 1, householdId: header.householdId, ownerUid: header.ownerUid,
    definitions, occurrences, memberUids: header.memberUids,
    defaultMemberUid: header.defaultMemberUid, legacyWorkloadCount: header.legacyWorkloadCount,
  };
  validateStagedMigration(manifest, header.householdId, header.ownerUid);
  if (canonicalMigrationDigest(manifest) !== header.digest) fail();
  return manifest;
}
