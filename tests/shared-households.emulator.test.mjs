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

async function call(name, data, user, expectedError) {
  const headers = { 'Content-Type': 'application/json' };
  if (user) headers.Authorization = 'Bearer ' + user.token;
  const response = await fetch(
    'http://' + functionHost + '/' + project + '/asia-northeast3/' + name,
    { method: 'POST', headers, body: JSON.stringify({ data }), signal: AbortSignal.timeout(45_000) },
  );
  const body = await response.json();
  if (expectedError) {
    assert.equal(body.error?.status, expectedError, name + ' returned unexpected error status');
    return;
  }
  assert.equal(response.status, 200, name + ' failed');
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
