import test from 'node:test';
import assert from 'node:assert/strict';
import { validMemberUid, normalizeMemberDisplayName, ownershipRevision, memberRosterDigest,
  assertDistinctMemberName } from '../membership-core.js';

test('display names are short normalized visible strings and UID paths are safe', () => {
  assert.equal(normalizeMemberDisplayName(' 가족 '), '가족');
  assert.equal(normalizeMemberDisplayName('e\u0301'), 'é');
  for (const name of ['', 'a'.repeat(13), 'A\nB', 'A\u202eB', '\ud800']) assert.throws(() => normalizeMemberDisplayName(name));
  for (const uid of ['', '.', '..', 'a/b', ' ']) assert.equal(validMemberUid(uid), false);
  assert.equal(validMemberUid('real-uid'), true);
});
test('preview detects role change, rejoin, profile change and active migration', () => {
  const members = [{uid:'a',isOwner:true,joinedIdentity:'owner:0',displayName:'Alice',profileRevision:1},
    {uid:'b',isOwner:false,joinedIdentity:'1:2',displayName:'Bob',profileRevision:1}];
  const digest = memberRosterDigest('home','a',0,members,false);
  assert.equal(digest, memberRosterDigest('home','a',0,[...members].reverse(),false));
  assert.notEqual(digest, memberRosterDigest('home','a',1,members,false));
  assert.notEqual(digest, memberRosterDigest('home','a',0,members,true));
  assert.notEqual(digest, memberRosterDigest('home','a',0,[members[0],{...members[1],joinedIdentity:'2:3'}],false));
  assert.notEqual(digest, memberRosterDigest('home','a',0,[members[0],{...members[1],profileRevision:2}],false));
  assert.throws(()=>assertDistinctMemberName(members,'b','ALICE'));
  assert.doesNotThrow(()=>assertDistinctMemberName(members,'a','ALICE'));
  assert.equal(ownershipRevision({}),0);
  assert.throws(()=>ownershipRevision({ownershipRevision:-1}));
});
