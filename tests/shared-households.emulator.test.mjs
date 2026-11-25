import { randomUUID } from 'node:crypto';
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, updateDoc, Timestamp } from 'firebase/firestore';
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
