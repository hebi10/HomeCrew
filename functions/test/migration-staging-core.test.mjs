import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { canonicalMigrationDigest, validateAtomicMigration } from '../migration-core.js';
import { remoteOccurrenceId } from '../sync-core.js';
import { stageHeader, stageChunk, appendStageCounts, assembleStagedManifest,
  MAX_CHUNK_ROWS, MAX_STAGE_BYTES } from '../migration-staging-core.js';

function fixture() {
  const id = createHash('sha256').update('homecrew-local-definition-v1:task').digest('hex');
  const def = { id, sourceLocalId:'task', name:'청소', category:'생활', description:'원본',
    points:2, assigneeUid:'owner', scheduledTime:'19:00', repeatLabel:'매일',
    schedule:{startEpochDay:20000,repeatMode:'DAILY',weekdays:[],intervalDays:2,
      assignmentMode:'FIXED',memberUids:['owner']},
    sourceWeekdayMask:0,sourceRepeatIntervalDays:2,sourceCreatedAtEpochMillis:1000,sourceUpdatedAtEpochMillis:2000 };
  const occurrences = Array.from({length:301},(_,i)=>({
    id:remoteOccurrenceId(id,20000+i),sourceLocalId:'row-'+i,definitionId:id,
    scheduledEpochDay:20000+i,assignedUid:'owner',workloadPoints:2,sourceWorkloadPoints:2,
    completed:false,completedByUid:null,completedAtEpochMillis:null,skippedAtEpochMillis:null,skipReason:null
  }));
  const manifest={version:1,householdId:'house',ownerUid:'owner',definitions:[def],occurrences,
    memberUids:['owner'],defaultMemberUid:'owner',legacyWorkloadCount:0};
  const chunks=[{index:0,definitions:[def],occurrences:[]}];
  for(let i=0;i<occurrences.length;i+=MAX_CHUNK_ROWS) chunks.push({
    index:chunks.length,definitions:[],occurrences:occurrences.slice(i,i+MAX_CHUNK_ROWS)
  });
  const header={version:1,householdId:'house',ownerUid:'owner',digest:canonicalMigrationDigest(manifest),
    definitionCount:1,occurrenceCount:301,memberUids:['owner'],defaultMemberUid:'owner',
    legacyWorkloadCount:0,chunkCount:chunks.length};
  return {manifest,chunks,header};
}
test('a larger manifest survives ordered chunks with the same canonical digest',()=>{
  const {manifest,chunks,header}=fixture();
  assert.throws(()=>validateAtomicMigration(manifest,'house','owner'));
  stageHeader(header,'house','owner');
  let progress={nextIndex:0,receivedDefinitions:0,receivedOccurrences:0,receivedBytes:0};
  for(const chunk of chunks) progress=appendStageCounts(header,progress,stageChunk(chunk));
  assert.equal(progress.nextIndex,header.chunkCount);
  assert.equal(progress.receivedOccurrences,301);
  assert.deepEqual(assembleStagedManifest(header,chunks),manifest);
});
test('rejects missing, changed, repeated, oversized and malformed chunks',()=>{
  const {chunks,header}=fixture();
  assert.throws(()=>assembleStagedManifest(header,chunks.slice(0,-1)));
  assert.throws(()=>assembleStagedManifest(header,[chunks[1],chunks[0],...chunks.slice(2)]));
  const changed=structuredClone(chunks);
  changed[1].occurrences[0].workloadPoints=5;
  assert.throws(()=>assembleStagedManifest(header,changed));
  const duplicate=structuredClone(chunks);
  duplicate[2].occurrences[0]=duplicate[1].occurrences[0];
  assert.throws(()=>assembleStagedManifest(header,duplicate));
  assert.throws(()=>stageChunk({...chunks[1],occurrences:[...chunks[1].occurrences,chunks[1].occurrences[0]]}));
  assert.throws(()=>stageHeader({...header,memberUids:['owner','owner']},'house','owner'));
  assert.throws(()=>stageHeader({...header,chunkCount:1},'house','owner'));
  assert.throws(()=>appendStageCounts(header,{nextIndex:0,receivedDefinitions:0,
    receivedOccurrences:0,receivedBytes:MAX_STAGE_BYTES},stageChunk(chunks[0])));
});
