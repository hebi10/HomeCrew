import { readFileSync } from 'node:fs';
import { before, beforeEach, after, test } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, deleteDoc, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc } from 'firebase/firestore';

let environment;
const rules = readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');

before(async () => {
  environment = await initializeTestEnvironment({
    projectId: 'demo-homecrew-security-ci',
    firestore: { rules },
  });
});
beforeEach(async () => { await environment.clearFirestore(); });
after(async () => { if (environment) await environment.cleanup(); });

function db(uid) {
  return uid ? environment.authenticatedContext(uid).firestore()
    : environment.unauthenticatedContext().firestore();
}
function home(name = '우리 집', ownerUid = 'alice') {
  return {
    name, ownerUid, schemaVersion: 1, memberCount: 1,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  };
}
async function createAliceHouse() {
  await assertSucceeds(setDoc(doc(db('alice'), 'households/house-one'), home()));
}
async function addServerMember(uid) {
  await environment.withSecurityRulesDisabled(async (admin) => {
    await setDoc(doc(admin.firestore(), `households/house-one/members/${uid}`), {
      uid, role: 'member', joinedAt: new Date(),
    });
  });
}

test('unauthenticated users cannot create or read any household', async () => {
  await assertFails(setDoc(doc(db(), 'households/visitor-house'), home('Visitor', 'visitor')));
  await createAliceHouse();
  await assertFails(getDoc(doc(db(), 'households/house-one')));
});

test('creator can read and rename their own house', async () => {
  await createAliceHouse();
  await assertSucceeds(getDoc(doc(db('alice'), 'households/house-one')));
  await assertSucceeds(updateDoc(doc(db('alice'), 'households/house-one'), {
    name: '우리 가족', updatedAt: serverTimestamp(),
  }));
  const result = await getDoc(doc(db('alice'), 'households/house-one'));
  if (result.data()?.name !== '우리 가족') throw new Error('household rename failed');
});

test('client cannot forge ownership or transfer owner to another account', async () => {
  await assertFails(setDoc(doc(db('bob'), 'households/house-one'), home('fake', 'alice')));
  await createAliceHouse();
  await assertFails(updateDoc(doc(db('alice'), 'households/house-one'), {
    ownerUid: 'bob', updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(db('alice'), 'households/house-one'), {
    schemaVersion: 2, updatedAt: serverTimestamp(),
  }));
  await assertFails(updateDoc(doc(db('alice'), 'households/house-one'), {
    memberCount: 8, updatedAt: serverTimestamp(),
  }));
  await assertFails(deleteDoc(doc(db('alice'), 'households/house-one')));
});

test('other users cannot read a household or list all households', async () => {
  await createAliceHouse();
  await assertFails(getDoc(doc(db('bob'), 'households/house-one')));
  await assertFails(getDocs(collection(db('alice'), 'households')));
  await assertFails(getDocs(collection(db('bob'), 'households')));
});

test('invited member can read but cannot change ownership or server-owned collections', async () => {
  await createAliceHouse();
  await addServerMember('bob');
  await assertSucceeds(getDoc(doc(db('bob'), 'households/house-one')));
  await assertFails(updateDoc(doc(db('bob'), 'households/house-one'), {
    name: '탈취', updatedAt: serverTimestamp(),
  }));
  await assertFails(setDoc(doc(db('bob'), 'households/house-one/chores/task-one'), { name: 'test' }));
  await assertFails(setDoc(doc(db('alice'), 'households/house-one/occurrences/occ-one'), { completed: true }));
  await assertFails(setDoc(doc(db('bob'), 'households/house-one/members/mallory'), { role: 'owner' }));
});

test('removed membership revokes reads, and cross-household member access is denied', async () => {
  await createAliceHouse();
  await addServerMember('bob');
  await environment.withSecurityRulesDisabled(async (admin) => {
    const store = admin.firestore();
    await setDoc(doc(store, 'households/other-house'), {
      name: 'other', ownerUid: 'charlie', schemaVersion: 1, memberCount: 1,
      createdAt: new Date(), updatedAt: new Date(),
    });
    await deleteDoc(doc(store, 'households/house-one/members/bob'));
  });
  await assertFails(getDoc(doc(db('bob'), 'households/house-one')));
  await assertFails(getDoc(doc(db('bob'), 'households/other-house')));
});

test('clients cannot write member indexes, local backups, tasks or unknown paths', async () => {
  await createAliceHouse();
  await assertFails(setDoc(doc(db('alice'), 'households/house-one/members/bob'), { uid: 'bob' }));
  await assertFails(setDoc(doc(db('alice'), 'users/alice/households/house-one'), { role: 'owner' }));
  await assertFails(setDoc(doc(db('alice'), 'users/bob'), { name: 'stolen' }));
  await assertFails(setDoc(doc(db('alice'), 'backups/private'), { contents: 'local-only' }));
});
