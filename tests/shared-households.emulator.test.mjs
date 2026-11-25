import { randomUUID } from 'node:crypto';
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc, updateDoc, Timestamp } from 'firebase/firestore';
import { inviteDigest } from '../functions/invite-core.js';

const project = 'demo-homecrew-shared-ci';
const authHost = '127.0.0.1:9099';
const firestoreHost = '127.0.0.1:8080';
const functionHost = '127.0.0.1:5001';
let environment;

before(async () => {
  // Fail closed: these tests cannot reach production or an arbitrary host.
  assert.equal(process.env.FIREBASE_AUTH_EMULATOR_HOST, authHost);
  assert.equal(process.env.FIRESTORE_EMULATOR_HOST, firestoreHost);
  assert.equal(process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT, project);
  environment = await initializeTestEnvironment({
    projectId: project,
    firestore: { host: '127.0.0.1', port: 8080 },
  });
});
after(async () => { if (environment) await environment.cleanup(); });

async function account() {
  const response = await fetch(
    'http://' + authHost + '/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: randomUUID() + '@example.invalid', password: randomUUID(), returnSecureToken: true,
      }),
      signal: AbortSignal.timeout(20_000),
    },
  );
  assert.equal(response.status, 200, 'Emulator account creation failed');
  const body = await response.json();
  assert.ok(typeof body.idToken === 'string' && typeof body.localId === 'string');
  return { token: body.idToken, uid: body.localId };
}

async function callRaw(name, data, user) {
  const headers = { 'Content-Type': 'application/json' };
  if (user) headers.Authorization = 'Bearer ' + user.token;
  const response = await fetch(
    'http://' + functionHost + '/' + project + '/asia-northeast3/' + name,
    { method: 'POST', headers, body: JSON.stringify({ data }), signal: AbortSignal.timeout(45_000) },
  );
  return { status: response.status, body: await response.json() };
}

async function call(name, data, user, expectedError) {
  const { status, body } = await callRaw(name, data, user);
  if (expectedError) {
    assert.equal(body.error?.status, expectedError, name + ' returned unexpected error status');
    return;
  }
  assert.equal(status, 200, name + ' failed');
  assert.ok(body.result && !body.error, name + ' did not return a successful result');
  return body.result;
}

async function read(path, user, expectedStatus = 200) {
  const response = await fetch(
    'http://' + firestoreHost + '/v1/projects/' + project + '/databases/(default)/documents/' + path,
    {
      headers: user ? { Authorization: 'Bearer ' + user.token } : {},
      signal: AbortSignal.timeout(20_000),
    },
  );
  assert.equal(response.status, expectedStatus, 'Unexpected Firestore read status for ' + path.split('/')[0]);
  return expectedStatus === 200 ? (await response.json()).fields : null;
}

test('atomic creation, invite membership, isolation, replay and revoked access', { timeout: 180_000 }, async () => {
  const [owner, member, outsider] = await Promise.all([account(), account(), account()]);
  const data = { name: '테스트 가족', requestId: randomUUID() };
  await call('createSharedHousehold', data, null, 'UNAUTHENTICATED');
  const first = await call('createSharedHousehold', data, owner);
  const replay = await call('createSharedHousehold', data, owner);
  assert.equal(first.householdId, replay.householdId);
  const id = first.householdId;
  assert.match(id, /^[a-f0-9]{64}$/);
  const homePath = 'households/' + id;
  const ownerIndex = await read('users/' + owner.uid + '/households/' + id, owner);
  assert.equal(ownerIndex.role.stringValue, 'owner');
  assert.equal((await read(homePath, owner)).memberCount.integerValue, '1');
  await read(homePath, member, 403);
  await read('users/' + owner.uid + '/households/' + id, outsider, 403);
  await call('createHouseholdInvite', { householdId: id }, outsider, 'PERMISSION_DENIED');

  const invite = await call('createHouseholdInvite', { householdId: id }, owner);
  assert.match(invite.inviteCode, /^[A-F0-9]{4}(-[A-F0-9]{4}){3}$/);
  const joined = await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, member);
  assert.equal(joined.householdId, id);
  assert.equal((await read(homePath, member)).memberCount.integerValue, '2');
  const memberIndex = await read('users/' + member.uid + '/households/' + id, member);
  assert.equal(memberIndex.role.stringValue, 'member');
  const joinedAgain = await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, member);
  assert.equal(joinedAgain.alreadyMember, true);
  assert.equal((await read(homePath, owner)).memberCount.integerValue, '2');
  await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, outsider, 'NOT_FOUND');
  await read(homePath, outsider, 403);
  await call('removeHouseholdMember', { householdId: id, memberUid: owner.uid }, member, 'PERMISSION_DENIED');

  const expiring = await call('createHouseholdInvite', { householdId: id }, owner);
  await environment.withSecurityRulesDisabled(async context => {
    await updateDoc(doc(context.firestore(), 'householdInvites/' + inviteDigest(expiring.inviteCode)), {
      expiresAt: Timestamp.fromMillis(Date.now() - 60_000),
    });
  });
  await call('acceptHouseholdInvite', { inviteCode: expiring.inviteCode }, outsider, 'NOT_FOUND');

  await call('removeHouseholdMember', { householdId: id, memberUid: member.uid }, owner);
  await read(homePath, member, 403);
  assert.equal((await read(homePath, owner)).memberCount.integerValue, '1');
  await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, member, 'NOT_FOUND');
  await read(homePath, member, 403);
});


test('shared chore occurrences are server-authorized and revision conflicts preserve the latest result', { timeout: 180_000 }, async () => {
  const [owner, member, outsider] = await Promise.all([account(), account(), account()]);
  const home = await call('createSharedHousehold', { name: '동기화 테스트', requestId: randomUUID() }, owner);
  const householdId = home.householdId;
  const invite = await call('createHouseholdInvite', { householdId }, owner);
  await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, member);

  const chore = {
    id: randomUUID(), name: '설거지', category: '주방', description: '',
    points: 2, assigneeUid: member.uid,
  };
  await call('createRemoteChore', { householdId, chore }, outsider, 'PERMISSION_DENIED');
  const created = await call('createRemoteChore', { householdId, chore }, owner);
  assert.equal(created.revision, 1);
  const today = Math.floor((Date.now() + 9 * 60 * 60 * 1000) / 86400000);
  const occurrence = await call('ensureRemoteOccurrence', {
    householdId, choreId: chore.id, scheduledEpochDay: today,
  }, owner);
  const sameOccurrence = await call('ensureRemoteOccurrence', {
    householdId, choreId: chore.id, scheduledEpochDay: today,
  }, member);
  assert.equal(occurrence.occurrenceId, sameOccurrence.occurrenceId);
  const path = 'households/' + householdId + '/occurrences/' + occurrence.occurrenceId;
  await read(path, outsider, 403);
  assert.equal((await read(path, member)).assignedUid.stringValue, member.uid);

  const completion = {
    householdId, occurrenceId: occurrence.occurrenceId,
    completed: true, expectedRevision: 1, requestId: randomUUID(),
  };
  const updated = await call('recordChoreCompletion', completion, member);
  assert.equal(updated.revision, 2);
  const replayed = await call('recordChoreCompletion', completion, member);
  assert.equal(replayed.revision, 2);
  assert.equal(replayed.replayed, true);
  const staleUndo = { ...completion, completed: false, requestId: randomUUID() };
  await call('recordChoreCompletion', staleUndo, owner, 'ABORTED');
  const confirmed = await read(path, owner);
  assert.equal(confirmed.completed.booleanValue, true);
  assert.equal(confirmed.completedByUid.stringValue, member.uid);
  assert.equal(confirmed.revision.integerValue, '2');

  const undone = await call('recordChoreCompletion', { ...staleUndo, expectedRevision: 2 }, owner);
  assert.equal(undone.revision, 3);
  assert.equal((await read(path, member)).completed.booleanValue, false);

  const raceA = { ...completion, expectedRevision: 3, requestId: randomUUID() };
  const raceB = { ...completion, expectedRevision: 3, requestId: randomUUID() };
  const concurrent = await Promise.all([
    callRaw('recordChoreCompletion', raceA, owner),
    callRaw('recordChoreCompletion', raceB, member),
  ]);
  const winner = concurrent.findIndex(result => result.status === 200 && result.body.result);
  assert.notEqual(winner, -1, 'One competing request must succeed');
  assert.equal(concurrent[1 - winner].body.error?.status, 'ABORTED');
  assert.equal(concurrent[winner].body.result.revision, 4);
  const winningCommand = winner === 0 ? raceA : raceB;
  const winningUser = winner === 0 ? owner : member;
  const replayedWinner = await call('recordChoreCompletion', winningCommand, winningUser);
  assert.equal(replayedWinner.replayed, true);
  assert.equal(replayedWinner.revision, 4);
  const latest = await read(path, owner);
  assert.equal(latest.revision.integerValue, '4');
  assert.equal(latest.completed.booleanValue, true);
  assert.equal(latest.completedByUid.stringValue, winningUser.uid);

  await call('removeHouseholdMember', { householdId, memberUid: member.uid }, owner);
  await read(path, member, 403);
  await call('recordChoreCompletion', {
    ...completion, expectedRevision: 4, requestId: randomUUID(),
  }, member, 'PERMISSION_DENIED');
});


test('server recurrence respects revision, stable rotation, horizon and existing completed records', { timeout: 180_000 }, async () => {
  const [owner, member, outsider] = await Promise.all([account(), account(), account()]);
  const { householdId } = await call('createSharedHousehold', {
    name: '반복 일정 테스트', requestId: randomUUID(),
  }, owner);
  const invite = await call('createHouseholdInvite', { householdId }, owner);
  await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, member);
  const chore = {
    id: randomUUID(), name: '정리', category: '청소', description: '',
    points: 2, assigneeUid: owner.uid,
  };
  await call('createRemoteChore', { householdId, chore }, owner);
  const today = Math.floor((Date.now() + 9 * 3600000) / 86400000);
  const schedule = {
    startEpochDay: today, repeatMode: 'CUSTOM', intervalDays: 2,
    assignmentMode: 'ROUND_ROBIN', memberUids: [owner.uid, member.uid],
  };
  const request = { householdId, choreId: chore.id, expectedRevision: 1, schedule };
  await call('configureRemoteSchedule', request, outsider, 'PERMISSION_DENIED');
  await call('configureRemoteSchedule', {
    ...request, schedule: { ...schedule, assignmentMode: 'UNKNOWN' },
  }, owner, 'INVALID_ARGUMENT');
  const configured = await call('configureRemoteSchedule', request, owner);
  assert.equal(configured.revision, 2);
  await call('configureRemoteSchedule', request, member, 'ABORTED');

  const range = { householdId, choreId: chore.id, expectedRevision: 2, fromEpochDay: today, toEpochDay: today + 4 };
  await call('generateRemoteSchedule', range, outsider, 'PERMISSION_DENIED');
  await call('generateRemoteSchedule', { ...range, expectedRevision: 1 }, member, 'ABORTED');
  const result = await call('generateRemoteSchedule', range, member);
  assert.equal(result.created, 3);
  const first = await call('ensureRemoteOccurrence', { householdId, choreId: chore.id, scheduledEpochDay: today }, owner);
  const second = await call('ensureRemoteOccurrence', { householdId, choreId: chore.id, scheduledEpochDay: today + 2 }, owner);
  const path = id => 'households/' + householdId + '/occurrences/' + id;
  assert.equal((await read(path(first.occurrenceId), owner)).assignedUid.stringValue, owner.uid);
  assert.equal((await read(path(second.occurrenceId), member)).assignedUid.stringValue, member.uid);
  await call('ensureRemoteOccurrence', { householdId, choreId: chore.id, scheduledEpochDay: today + 1 }, owner, 'FAILED_PRECONDITION');
  await call('recordChoreCompletion', {
    householdId, occurrenceId: second.occurrenceId, completed: true,
    expectedRevision: 1, requestId: randomUUID(),
  }, member);
  const again = await call('generateRemoteSchedule', range, owner);
  assert.equal(again.created, 0);
  assert.equal(again.existing, 3);
  const preserved = await read(path(second.occurrenceId), owner);
  assert.equal(preserved.completed.booleanValue, true);
  assert.equal(preserved.revision.integerValue, '2');

  await call('generateRemoteSchedule', { ...range, fromEpochDay: today + 63, toEpochDay: today + 64 }, owner, 'INVALID_ARGUMENT');
  await call('removeHouseholdMember', { householdId, memberUid: member.uid }, owner);
  await call('generateRemoteSchedule', { ...range, fromEpochDay: today + 6, toEpochDay: today + 6 }, owner, 'FAILED_PRECONDITION');
  assert.equal((await read(path(second.occurrenceId), owner)).completed.booleanValue, true);
});


test('balanced concurrent generation shares weekly workload without changing existing assignments', { timeout: 180_000 }, async () => {
  const [owner, member] = await Promise.all([account(), account()]);
  const { householdId } = await call('createSharedHousehold', {
    name: '균형 배정 검증', requestId: randomUUID(),
  }, owner);
  const invite = await call('createHouseholdInvite', { householdId }, owner);
  await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, member);
  const today = Math.floor((Date.now() + 9 * 3600000) / 86400000);
  async function create(points, name, balanced) {
    const chore = { id: randomUUID(), name, category: '청소', description: '', points, assigneeUid: owner.uid };
    await call('createRemoteChore', { householdId, chore }, owner);
    if (!balanced) return chore.id;
    await call('configureRemoteSchedule', {
      householdId, choreId: chore.id, expectedRevision: 1,
      schedule: { startEpochDay: today, repeatMode: 'DAILY', assignmentMode: 'BALANCED', memberUids: [owner.uid, member.uid] },
    }, owner);
    return chore.id;
  }
  const baseline = await create(5, '기존 부담', false);
  const original = await call('ensureRemoteOccurrence', { householdId, choreId: baseline, scheduledEpochDay: today }, owner);
  const [a, b, c] = await Promise.all([
    create(5, '새 작업 A', true), create(5, '새 작업 B', true), create(5, '새 작업 C', true),
  ]);
  const [first, second] = await Promise.all([
    call('generateRemoteSchedule', { householdId, choreId: a, expectedRevision: 2, fromEpochDay: today, toEpochDay: today }, owner),
    call('generateRemoteSchedule', { householdId, choreId: b, expectedRevision: 2, fromEpochDay: today, toEpochDay: today }, member),
  ]);
  assert.equal(first.created, 1);
  assert.equal(second.created, 1);
  await call('ensureRemoteOccurrence', { householdId, choreId: c, scheduledEpochDay: today }, member);
  async function occurrence(choreId) {
    const { occurrenceId } = await call('ensureRemoteOccurrence', { householdId, choreId, scheduledEpochDay: today }, owner);
    return read('households/' + householdId + '/occurrences/' + occurrenceId, owner);
  }
  const rows = await Promise.all([baseline, a, b, c].map(occurrence));
  assert.equal(rows.filter(r => r.assignedUid.stringValue === owner.uid).length, 2);
  assert.equal(rows.filter(r => r.assignedUid.stringValue === member.uid).length, 2);
  assert.ok(rows.every(row => row.workloadPoints.integerValue === '5'));
  const old = await read('households/' + householdId + '/occurrences/' + original.occurrenceId, owner);
  assert.equal(old.assignedUid.stringValue, owner.uid);
  assert.equal(old.revision.integerValue, '1');
  await call('removeHouseholdMember', { householdId, memberUid: member.uid }, owner);
  await call('generateRemoteSchedule', { householdId, choreId: a, expectedRevision: 2,
    fromEpochDay: today + 1, toEpochDay: today + 1 }, owner, 'FAILED_PRECONDITION');
});


test('future reconciliation requires owner, exact preview and preserves touched history', { timeout: 180_000 }, async () => {
  const [owner, member] = await Promise.all([account(), account()]);
  const { householdId } = await call('createSharedHousehold', { name: '미래 일정 검증', requestId: randomUUID() }, owner);
  const invite = await call('createHouseholdInvite', { householdId }, owner);
  await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, member);
  const chore = { id: randomUUID(), name: '정리', category: '청소', description: '', points: 3, assigneeUid: owner.uid };
  await call('createRemoteChore', { householdId, chore }, owner);
  const today = Math.floor((Date.now() + 9 * 3600000) / 86400000);
  const scope = { householdId, choreId: chore.id };
  const daily = { startEpochDay: today, repeatMode: 'DAILY', assignmentMode: 'FIXED', memberUids: [owner.uid] };
  await call('configureRemoteSchedule', { ...scope, expectedRevision: 1, schedule: daily }, owner);
  await call('generateRemoteSchedule', { ...scope, expectedRevision: 2, fromEpochDay: today, toEpochDay: today + 5 }, owner);
  const ids = await Promise.all([0,1,2,3,4,5].map(offset => call('ensureRemoteOccurrence', {
    ...scope, scheduledEpochDay: today + offset,
  }, owner)));
  const path = i => 'households/' + householdId + '/occurrences/' + ids[i].occurrenceId;
  async function complete(i, revision, completed) {
    return call('recordChoreCompletion', { householdId, occurrenceId: ids[i].occurrenceId,
      expectedRevision: revision, completed, requestId: randomUUID() }, owner);
  }
  await complete(3, 1, true);
  await complete(3, 2, false);
  const changed = { ...daily, repeatMode: 'CUSTOM', intervalDays: 2, memberUids: [member.uid] };
  await call('configureRemoteSchedule', { ...scope, expectedRevision: 2, schedule: changed }, owner);
  const request = { ...scope, expectedRevision: 3 };
  await call('previewRemoteFutureSchedule', request, member, 'PERMISSION_DENIED');
  const first = await call('previewRemoteFutureSchedule', request, owner);
  assert.equal(first.cancelled, 2);
  assert.equal(first.reassigned, 2);
  assert.equal(first.protectedCount, 1);
  assert.equal((await read(path(1), owner)).revision.integerValue, '1');
  await complete(1, 1, true);
  await call('applyRemoteFutureSchedule', { ...request, previewDigest: first.previewDigest }, owner, 'ABORTED');
  const next = await call('previewRemoteFutureSchedule', request, owner);
  assert.equal(next.cancelled, 1);
  assert.equal(next.protectedCount, 2);
  const applied = await call('applyRemoteFutureSchedule', { ...request, previewDigest: next.previewDigest }, owner);
  assert.equal(applied.cancelled, 1);
  assert.equal(applied.reassigned, 2);
  assert.equal((await read(path(0), owner)).revision.integerValue, '1');
  assert.equal((await read(path(1), owner)).completed.booleanValue, true);
  assert.equal((await read(path(3), owner)).revision.integerValue, '3');
  assert.ok((await read(path(5), owner)).scheduleCancelledAt.timestampValue);
  assert.equal((await read(path(2), owner)).assignedUid.stringValue, member.uid);
  await call('recordChoreCompletion', { householdId, occurrenceId: ids[5].occurrenceId,
    expectedRevision: 2, completed: true, requestId: randomUUID() }, owner, 'FAILED_PRECONDITION');
  const replay = await call('applyRemoteFutureSchedule', { ...request, previewDigest: next.previewDigest }, owner);
  assert.equal(replay.replayed, true);
  await call('configureRemoteSchedule', { ...scope, expectedRevision: 3, schedule: daily }, owner);
  const restoreRequest = { ...scope, expectedRevision: 4 };
  const restore = await call('previewRemoteFutureSchedule', restoreRequest, owner);
  assert.equal(restore.restored, 1);
  await call('applyRemoteFutureSchedule', { ...restoreRequest, previewDigest: restore.previewDigest }, owner);
  const restored = await read(path(5), owner);
  assert.ok(restored.scheduleCancelledAt.nullValue === null || restored.scheduleCancelledAt.nullValue === 'NULL_VALUE');
  assert.equal(restored.assignedUid.stringValue, owner.uid);
});


test('local manifest import is atomic, owner-bound, idempotent and preserves history', { timeout: 180_000 }, async () => {
  const { canonicalMigrationDigest } = await import('../functions/migration-core.js');
  const { remoteOccurrenceId } = await import('../functions/sync-core.js');
  const { createHash } = await import('node:crypto');
  const [owner, member, outsider] = await Promise.all([account(), account(), account()]);
  const { householdId } = await call('createSharedHousehold', { name: '이전 검증', requestId: randomUUID() }, owner);
  const invite = await call('createHouseholdInvite', { householdId }, owner);
  await call('acceptHouseholdInvite', { inviteCode: invite.inviteCode }, member);
  const day = Math.floor((Date.now() + 9 * 3600000) / 86400000);
  const id = createHash('sha256').update('homecrew-local-definition-v1:local-task').digest('hex');
  const definition = { id, sourceLocalId: 'local-task', name: '설거지', category: '주방', description: '원본',
    points: 3, assigneeUid: owner.uid, scheduledTime: '19:20', repeatLabel: '매일',
    schedule: { startEpochDay: day, repeatMode: 'DAILY', weekdays: [], intervalDays: 2,
      assignmentMode: 'FIXED', memberUids: [owner.uid] },
    sourceWeekdayMask: 0, sourceRepeatIntervalDays: 2,
    sourceCreatedAtEpochMillis: 1000, sourceUpdatedAtEpochMillis: 2000 };
  const first = { id: remoteOccurrenceId(id, day - 1), sourceLocalId: 'past', definitionId: id,
    scheduledEpochDay: day - 1, assignedUid: member.uid, workloadPoints: 3, sourceWorkloadPoints: 0,
    completed: true, completedByUid: owner.uid, completedAtEpochMillis: 1500, skippedAtEpochMillis: null, skipReason: null };
  const second = { ...first, id: remoteOccurrenceId(id, day), sourceLocalId: 'skip', scheduledEpochDay: day,
    completed: false, completedByUid: null, completedAtEpochMillis: null, skippedAtEpochMillis: 1700, skipReason: '외출' };
  const manifest = { version: 1, householdId, ownerUid: owner.uid, definitions: [definition],
    occurrences: [first, second], memberUids: [owner.uid, member.uid], defaultMemberUid: owner.uid, legacyWorkloadCount: 2 };
  const request = value => ({ householdId, manifest: value, digest: canonicalMigrationDigest(value) });
  const path = 'households/' + householdId;
  await call('importLocalHouseholdManifest', request(manifest), null, 'UNAUTHENTICATED');
  await call('importLocalHouseholdManifest', request({ ...manifest, ownerUid: member.uid }), member, 'PERMISSION_DENIED');
  await call('importLocalHouseholdManifest', { ...request(manifest), digest: '0'.repeat(64) }, owner, 'INVALID_ARGUMENT');
  const malformed = structuredClone(manifest);
  malformed.occurrences[1].workloadPoints = 1;
  await call('importLocalHouseholdManifest', request(malformed), owner, 'INVALID_ARGUMENT');
  await read(path + '/chores/' + id, owner, 404);
  await call('removeHouseholdMember', { householdId, memberUid: member.uid }, owner);
  await call('importLocalHouseholdManifest', request(manifest), owner, 'FAILED_PRECONDITION');
  await read(path + '/chores/' + id, owner, 404);
  const renewed = await call('createHouseholdInvite', { householdId }, owner);
  await call('acceptHouseholdInvite', { inviteCode: renewed.inviteCode }, member);
  const receiptRequest = { householdId, digest: canonicalMigrationDigest(manifest) };
  assert.equal((await call('getLocalHouseholdMigrationReceipt', receiptRequest, owner)).found, false);
  await call('getLocalHouseholdMigrationReceipt', receiptRequest, member, 'PERMISSION_DENIED');
  const imported = await call('importLocalHouseholdManifest', request(manifest), owner);
  assert.equal(imported.definitions, 1);
  assert.equal(imported.occurrences, 2);
  assert.equal(imported.completed, 1);
  assert.equal(imported.skipped, 1);
  const def = await read(path + '/chores/' + id, member);
  assert.equal(def.scheduledTime.stringValue, '19:20');
  assert.equal(def.sourceCreatedAtEpochMillis.integerValue, '1000');
  const history = await read(path + '/occurrences/' + first.id, member);
  assert.equal(history.completed.booleanValue, true);
  assert.equal(history.completedByUid.stringValue, owner.uid);
  assert.equal(history.completedAtEpochMillis.integerValue, '1500');
  assert.equal(history.sourceWorkloadPoints.integerValue, '0');
  const skipped = await read(path + '/occurrences/' + second.id, owner);
  assert.equal(skipped.skipReason.stringValue, '외출');
  assert.equal(skipped.skippedAtEpochMillis.integerValue, '1700');
  await read(path + '/occurrences/' + first.id, outsider, 403);
  const replay = await call('importLocalHouseholdManifest', request(manifest), owner);
  assert.equal(replay.replayed, true);
  assert.equal(replay.digest, imported.digest);
  const receipt = await call('getLocalHouseholdMigrationReceipt', receiptRequest, owner);
  assert.equal(receipt.found, true);
  assert.equal(receipt.digest, imported.digest);
  assert.equal(receipt.occurrences, 2);
  await call('getLocalHouseholdMigrationReceipt', receiptRequest, outsider, 'PERMISSION_DENIED');
  const changed = structuredClone(manifest);
  changed.definitions[0].name = '덮어쓰기 금지';
  await call('importLocalHouseholdManifest', request(changed), owner, 'FAILED_PRECONDITION');
  assert.equal((await read(path + '/chores/' + id, owner)).name.stringValue, '설거지');
});


test('large migration staging is private, ordered, resumable and never publishes partial rows', { timeout: 180_000 }, async () => {
  const { canonicalMigrationDigest } = await import('../functions/migration-core.js');
  const { remoteOccurrenceId } = await import('../functions/sync-core.js');
  const { createHash } = await import('node:crypto');
  const [owner, member] = await Promise.all([account(), account()]);
  const { householdId } = await call('createSharedHousehold', { name:'분할 이전 검증', requestId:randomUUID() }, owner);
  const invite=await call('createHouseholdInvite',{householdId},owner);
  await call('acceptHouseholdInvite',{inviteCode:invite.inviteCode},member);
  const id=createHash('sha256').update('homecrew-local-definition-v1:task').digest('hex');
  const definition={id,sourceLocalId:'task',name:'청소',category:'생활',description:'원본',points:2,
    assigneeUid:member.uid,scheduledTime:'19:00',repeatLabel:'매일',
    schedule:{startEpochDay:20000,repeatMode:'DAILY',weekdays:[],intervalDays:2,
      assignmentMode:'FIXED',memberUids:[member.uid]},
    sourceWeekdayMask:0,sourceRepeatIntervalDays:2,sourceCreatedAtEpochMillis:1000,sourceUpdatedAtEpochMillis:2000};
  const occurrences=Array.from({length:301},(_,i)=>({
    id:remoteOccurrenceId(id,20000+i),sourceLocalId:'row-'+i,definitionId:id,
    scheduledEpochDay:20000+i,assignedUid:member.uid,workloadPoints:2,sourceWorkloadPoints:2,
    completed:false,completedByUid:null,completedAtEpochMillis:null,skippedAtEpochMillis:null,skipReason:null
  }));
  const manifest={version:1,householdId,ownerUid:owner.uid,definitions:[definition],occurrences,
    memberUids:[owner.uid,member.uid],defaultMemberUid:owner.uid,legacyWorkloadCount:0};
  const chunks=[{index:0,definitions:[definition],occurrences:[]}];
  for(let i=0;i<occurrences.length;i+=100) chunks.push({index:chunks.length,definitions:[],occurrences:occurrences.slice(i,i+100)});
  const header={version:1,householdId,ownerUid:owner.uid,digest:canonicalMigrationDigest(manifest),
    definitionCount:1,occurrenceCount:301,memberUids:manifest.memberUids,defaultMemberUid:owner.uid,
    legacyWorkloadCount:0,chunkCount:chunks.length};
  await call('getLocalMigrationStage',{householdId},member,'PERMISSION_DENIED');
  const started=await call('beginLocalMigrationStage',{householdId,header},owner);
  assert.equal(started.state,'UPLOADING');
  const scope={householdId,sessionId:started.sessionId};
  const append=(chunk,user=owner,error)=>call('appendLocalMigrationStageChunk',{
    ...scope,chunk,chunkDigest:canonicalMigrationDigest(chunk)},user,error);
  assert.equal((await call('beginLocalMigrationStage',{householdId,header},owner)).sessionId,started.sessionId);
  await call('beginLocalMigrationStage',{householdId,header:{...header,digest:'f'.repeat(64)}},owner,'ALREADY_EXISTS');
  await append(chunks[0],member,'PERMISSION_DENIED');
  await append(chunks[1],owner,'FAILED_PRECONDITION');
  assert.equal((await append(chunks[0])).nextIndex,1);
  assert.equal((await append(chunks[0])).replayed,true);
  await append({...chunks[0],definitions:[{...definition,name:'다른 내용'}]},owner,'ALREADY_EXISTS');
  await call('sealLocalMigrationStage',scope,owner,'FAILED_PRECONDITION');
  const recovered=await call('getLocalMigrationStage',{householdId},owner);
  assert.equal(recovered.nextIndex,1);
  const home='households/'+householdId;
  await read(home+'/migrationStaging/current',owner,403);
  await read(home+'/migrationStaging/current/migrationChunks/0000',member,403);
  await read(home+'/chores/'+id,owner,404);
  for(const chunk of chunks.slice(recovered.nextIndex)) await append(chunk);
  await call('removeHouseholdMember',{householdId,memberUid:member.uid},owner);
  await call('sealLocalMigrationStage',scope,owner,'FAILED_PRECONDITION');
  const renewed=await call('createHouseholdInvite',{householdId},owner);
  await call('acceptHouseholdInvite',{inviteCode:renewed.inviteCode},member);
  const sealed=await call('sealLocalMigrationStage',scope,owner);
  assert.equal(sealed.state,'SEALED');
  assert.equal(sealed.receivedOccurrences,301);
  assert.equal((await call('sealLocalMigrationStage',scope,owner)).state,'SEALED');
  await read(home+'/chores/'+id,owner,404);
  await read(home+'/occurrences/'+occurrences[0].id,member,404);
  const publish = (session, index, user=owner, error) => call('publishLocalMigrationStageChunk',
    {householdId,sessionId:session,digest:header.digest,index},user,error);
  await publish(started.sessionId,0,member,'PERMISSION_DENIED');
  await publish(started.sessionId,1,owner,'FAILED_PRECONDITION');
  assert.equal((await publish(started.sessionId,0)).publishIndex,1);
  assert.equal((await publish(started.sessionId,0)).replayed,true);
  await read(home+'/chores/'+id,owner,403);
  await read(home+'/occurrences/'+occurrences[0].id,member,403);
  await call('createRemoteChore',{householdId,chore:{
    id:randomUUID(),name:'차단',category:'생활',description:'',points:1,assigneeUid:owner.uid,
  }},member,'FAILED_PRECONDITION');
  await call('cancelLocalMigrationStage',scope,owner,'FAILED_PRECONDITION');
  assert.equal((await call('getLocalHouseholdMigrationReceipt',{householdId,digest:header.digest},owner)).found,false);
  assert.equal((await publish(started.sessionId,1)).publishIndex,2);
  await read(home+'/occurrences/'+occurrences[0].id,owner,403);
  await call('removeHouseholdMember',{householdId,memberUid:member.uid},owner);
  await publish(started.sessionId,2,owner,'FAILED_PRECONDITION');
  const rollback=await call('rollbackLocalMigrationPublication',scope,owner);
  assert.equal(rollback.rolledBack,false);
  await read(home+'/chores/'+id,owner,403);
  assert.equal((await call('rollbackLocalMigrationPublication',scope,owner)).rolledBack,true);
  await read(home+'/chores/'+id,owner,404);
  assert.equal((await call('cancelLocalMigrationStage',scope,owner)).removed,true);
  assert.equal((await call('getLocalMigrationStage',{householdId},owner)).found,false);
  const rejoin=await call('createHouseholdInvite',{householdId},owner);
  await call('acceptHouseholdInvite',{inviteCode:rejoin.inviteCode},member);
  const another=await call('beginLocalMigrationStage',{householdId,header},owner);
  assert.notEqual(another.sessionId,started.sessionId);
  await append(chunks[0],owner,'NOT_FOUND');
  const nextScope={householdId,sessionId:another.sessionId};
  for(const chunk of chunks) await call('appendLocalMigrationStageChunk',{
    ...nextScope,chunk,chunkDigest:canonicalMigrationDigest(chunk)},owner);
  await call('sealLocalMigrationStage',nextScope,owner);
  for(let index=0;index<chunks.length;index++) {
    const result=await publish(another.sessionId,index);
    if(index<chunks.length-1) {
      assert.equal(result.published,false);
      await read(home+'/chores/'+id,member,403);
    } else {
      assert.equal(result.published,true);
      assert.equal(result.occurrences,301);
    }
  }
  assert.equal((await read(home+'/chores/'+id,member)).name.stringValue,'청소');
  assert.equal((await read(home+'/occurrences/'+occurrences[0].id,member)).assignedUid.stringValue,member.uid);
  assert.equal((await publish(another.sessionId,chunks.length-1)).replayed,true);
  await call('rollbackLocalMigrationPublication',nextScope,owner,'FAILED_PRECONDITION');
  assert.equal((await call('cleanupPublishedMigrationStage',nextScope,owner)).removed,true);
  assert.equal((await publish(another.sessionId,chunks.length-1)).replayed,true);
  assert.equal((await call('getLocalHouseholdMigrationReceipt',{householdId,digest:header.digest},owner)).occurrences,301);
  await call('recordChoreCompletion',{householdId,occurrenceId:occurrences[0].id,
    expectedRevision:1,completed:true,requestId:randomUUID()},member);
  assert.equal((await read(home+'/occurrences/'+occurrences[0].id,owner)).completed.booleanValue,true);
});


test('member profiles, stale previews, owner transfer, invite epoch and historical assignment preservation', {timeout:180_000}, async()=>{
  const [owner, member, outsider]=await Promise.all([account(),account(),account()]);
  const {householdId}=await call('createSharedHousehold',{name:'구성원 검증',requestId:randomUUID()},owner);
  const scope={householdId};
  const invite=await call('createHouseholdInvite',scope,owner);
  await call('acceptHouseholdInvite',{inviteCode:invite.inviteCode},member);
  const oldInvite=await call('createHouseholdInvite',scope,owner);
  const roster=who=>call('getHouseholdRoster',scope,who);
  const change=(who,action,memberUid,expectedRosterDigest,error)=>call('changeHouseholdMembership',
    {...scope,action,memberUid,expectedRosterDigest},who,error);
  const rename=(who,memberUid,displayName,expectedRosterDigest,error)=>call('updateHouseholdMemberName',
    {...scope,memberUid,displayName,expectedRosterDigest},who,error);
  await call('getHouseholdRoster',scope,outsider,'PERMISSION_DENIED');
  const original=await roster(owner);
  assert.equal(original.members.length,2);
  assert.equal(original.members.find(p=>p.uid===owner.uid).isOwner,true);
  await rename(member,owner.uid,'변경 불가',original.rosterDigest,'PERMISSION_DENIED');
  await rename(owner,owner.uid,'소유자',original.rosterDigest);
  await rename(member,member.uid,'가족',original.rosterDigest,'ABORTED');
  const current=await roster(member);
  await rename(member,member.uid,'가족',current.rosterDigest);
  const named=await roster(owner);
  assert.equal(named.members.find(p=>p.uid===member.uid).displayName,'가족');
  await rename(owner,member.uid,'소유자',named.rosterDigest,'ALREADY_EXISTS');
  await read('households/'+householdId+'/memberProfiles/'+member.uid,outsider,403);
  await change(member,'REMOVE',owner.uid,named.rosterDigest,'PERMISSION_DENIED');
  await change(owner,'LEAVE',owner.uid,named.rosterDigest,'FAILED_PRECONDITION');
  const stage=await call('beginLocalMigrationStage',{householdId,header:{
    version:1,householdId,ownerUid:owner.uid,digest:'a'.repeat(64),
    definitionCount:1,occurrenceCount:0,memberUids:[owner.uid,member.uid],
    defaultMemberUid:owner.uid,legacyWorkloadCount:0,chunkCount:1,
  }},owner);
  const blocked=await roster(owner);
  assert.equal(blocked.transferBlocked,true);
  await change(owner,'TRANSFER',member.uid,blocked.rosterDigest,'FAILED_PRECONDITION');
  await call('cancelLocalMigrationStage',{householdId,sessionId:stage.sessionId},owner);
  assert.equal((await roster(owner)).transferBlocked,false);

  const task={id:randomUUID(),name:'설거지',category:'주방',description:'',points:3,assigneeUid:member.uid};
  await call('createRemoteChore',{householdId,chore:task},owner);
  const day=Math.floor((Date.now()+9*3600000)/86400000);
  await call('configureRemoteSchedule',{householdId,choreId:task.id,expectedRevision:1,schedule:{
    startEpochDay:day,repeatMode:'DAILY',assignmentMode:'ROUND_ROBIN',memberUids:[member.uid,owner.uid],
  }},owner);
  const occurrence=await call('ensureRemoteOccurrence',{householdId,choreId:task.id,scheduledEpochDay:day},owner);
  const occurrencePath='households/'+householdId+'/occurrences/'+occurrence.occurrenceId;
  await call('recordChoreCompletion',{householdId,occurrenceId:occurrence.occurrenceId,
    completed:true,expectedRevision:1,requestId:randomUUID()},member);
  await change(owner,'TRANSFER',member.uid,original.rosterDigest,'ABORTED');
  const transferred=await change(owner,'TRANSFER',member.uid,(await roster(owner)).rosterDigest);
  assert.equal(transferred.changed,true);
  const after=await roster(member);
  assert.equal(after.members.find(p=>p.uid===member.uid).isOwner,true);
  assert.equal(after.members.find(p=>p.uid===owner.uid).isOwner,false);
  assert.equal(after.members.find(p=>p.uid===member.uid).displayName,'가족');
  assert.equal((await read('users/'+owner.uid+'/households/'+householdId,owner)).role.stringValue,'member');
  assert.equal((await read('users/'+member.uid+'/households/'+householdId,member)).role.stringValue,'owner');
  await call('createHouseholdInvite',scope,owner,'PERMISSION_DENIED');
  await change(member,'TRANSFER',owner.uid,after.rosterDigest);
  await call('acceptHouseholdInvite',{inviteCode:oldInvite.inviteCode},outsider,'NOT_FOUND');
  const restored=await roster(owner);
  await change(member,'LEAVE',member.uid,restored.rosterDigest);
  await read(occurrencePath,member,403);
  const history=await read(occurrencePath,owner);
  assert.equal(history.completed.booleanValue,true);
  assert.equal(history.completedByUid.stringValue,member.uid);
  assert.equal(history.assignedUid.stringValue,member.uid);
  await read('households/'+householdId+'/memberProfiles/'+member.uid,owner,404);
  await call('generateRemoteSchedule',{householdId,choreId:task.id,expectedRevision:2,
    fromEpochDay:day+1,toEpochDay:day+2},owner,'FAILED_PRECONDITION');
  const rejoin=await call('createHouseholdInvite',scope,owner);
  await call('acceptHouseholdInvite',{inviteCode:rejoin.inviteCode},member);
  const joined=await roster(owner);
  await change(owner,'REMOVE',member.uid,restored.rosterDigest,'ABORTED');
  await change(owner,'REMOVE',member.uid,joined.rosterDigest);
  assert.equal((await roster(owner)).members.length,1);
  assert.equal((await read(occurrencePath,owner)).completed.booleanValue,true);
});


test('account deletion preflight inventories current and historical references without deleting anything', { timeout: 180_000 }, async () => {
  const [owner, member, outsider] = await Promise.all([account(), account(), account()]);
  const { householdId } = await call('createSharedHousehold', {name:'계정 검증',requestId:randomUUID()},owner);
  const invite=await call('createHouseholdInvite',{householdId},owner);
  await call('acceptHouseholdInvite',{inviteCode:invite.inviteCode},member);
  const preflight = (who,data={})=>call('getAccountDeletionPreflight',data,who);
  await call('getAccountDeletionPreflight',{},null,'UNAUTHENTICATED');
  const ownerPlan=await preflight(owner);
  assert.equal(ownerPlan.ownerCount,1);
  assert.equal(ownerPlan.memberCount,0);
  assert.equal(ownerPlan.indexIssues,0);
  assert.equal(ownerPlan.hasInvites,true);
  assert.equal(ownerPlan.deletionAllowed,false);
  const memberPlan=await preflight(member,{uid:owner.uid});
  assert.equal(memberPlan.ownerCount,0);
  assert.equal(memberPlan.memberCount,1);
  const outsidePlan=await preflight(outsider,{uid:owner.uid});
  assert.deepEqual(outsidePlan.spaces,[]);
  assert.equal(outsidePlan.deletionAllowed,false);
  await environment.withSecurityRulesDisabled(async context=>{
    await setDoc(doc(context.firestore(),'users/'+outsider.uid+'/households/'+householdId),
      {householdId,role:'member',updatedAt:Timestamp.now()});
  });
  const stale=await preflight(outsider);
  assert.deepEqual(stale.spaces,[]);
  assert.equal(stale.indexIssues,1);
  const task={id:randomUUID(),name:'설거지',category:'주방',description:'',points:2,assigneeUid:member.uid};
  await call('createRemoteChore',{householdId,chore:task},owner);
  const day=Math.floor((Date.now()+9*3600000)/86400000);
  const row=await call('ensureRemoteOccurrence',{householdId,choreId:task.id,scheduledEpochDay:day},owner);
  await call('recordChoreCompletion',{householdId,occurrenceId:row.occurrenceId,completed:true,
    expectedRevision:1,requestId:randomUUID()},member);
  await call('leaveHousehold',{householdId},member);
  const former=await preflight(member);
  assert.equal(former.memberCount,0);
  assert.equal(former.hasTaskReferences,true);
  assert.equal(former.hasOccurrenceReferences,true);
  assert.equal(former.requiresCleanupReview,true);
  assert.equal(former.deletionAllowed,false);
  const historical=await read('households/'+householdId+'/occurrences/'+row.occurrenceId,owner);
  assert.equal(historical.completed.booleanValue,true);
  assert.equal(historical.completedByUid.stringValue,member.uid);
  // A legacy direct owner without an index must be found independently.
  const legacyId='legacy-'+randomUUID();
  await environment.withSecurityRulesDisabled(async context=>{
    await setDoc(doc(context.firestore(),'households/'+legacyId), {
      name:'기존 집',ownerUid:owner.uid,schemaVersion:1,memberCount:1,createdAt:Timestamp.now(),updatedAt:Timestamp.now(),
    });
  });
  const unindexed=await preflight(owner);
  assert.equal(unindexed.ownerCount,2);
  assert.equal(unindexed.indexIssues,1);
  assert.equal(unindexed.deletionAllowed,false);
});
