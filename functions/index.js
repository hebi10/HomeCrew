import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { initializeApp } from 'firebase-admin/app';
import { FieldValue, Timestamp, getFirestore } from 'firebase-admin/firestore';
import {
  INVITE_TTL_MS, MAX_HOUSEHOLD_MEMBERS, createInviteCode, inviteDigest,
  normalizeInviteCode, rateLimitNext, requireHouseholdId,
  requireAvailableMemberSlot, requireRemovableMemberCount,
} from './invite-core.js';
import { createHash } from 'node:crypto';

export { createSharedHousehold } from './household-create.js';

initializeApp();
const db = getFirestore();
const options = { region: 'asia-northeast3', maxInstances: 10, timeoutSeconds: 30 };

function uidOf(request) {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError('unauthenticated', 'Firebase account is required.');
  return uid;
}
function houseRef(householdId) {
  try { return db.doc('households/' + requireHouseholdId(householdId)); }
  catch { throw new HttpsError('invalid-argument', 'Invalid household.'); }
}
function inviteRef(code) {
  return db.doc('householdInvites/' + inviteDigest(code));
}
function rateRef(uid, action) {
  const hash = createHash('sha256').update(uid).digest('hex');
  return db.doc('inviteRateLimits/' + action + '-' + hash);
}
function applyRateLimit(transaction, reference, current, now, max) {
  let next;
  try { next = rateLimitNext(current?.data(), now, max); }
  catch { throw new HttpsError('resource-exhausted', 'Too many requests. Please try later.'); }
  transaction.set(reference, next, { merge: true });
}

async function chargeRateLimit(uid, action, max) {
  const reference = rateRef(uid, action);
  await db.runTransaction(async (tx) => {
    const current = await tx.get(reference);
    applyRateLimit(tx, reference, current, Date.now(), max);
  });
}
function invalidInvite() {
  throw new HttpsError('not-found', 'Invite is invalid or expired.');
}

export const ensureHouseholdOwnerIndex = onCall(options, async (request) => {
  const uid = uidOf(request);
  const home = houseRef(request.data?.householdId);
  const index = db.doc(`users/${uid}/households/${home.id}`);
  await db.runTransaction(async (tx) => {
    const house = await tx.get(home);
    if (!house.exists || house.data().ownerUid !== uid) {
      throw new HttpsError('permission-denied', 'Household owner required.');
    }
    tx.set(index, { householdId: home.id, role: 'owner', updatedAt: FieldValue.serverTimestamp() });
  });
  return { householdId: home.id };
});

export const createHouseholdInvite = onCall(options, async (request) => {
  const uid = uidOf(request);
  const home = houseRef(request.data?.householdId);
  const code = createInviteCode();
  const invite = inviteRef(code);
  const rate = rateRef(uid, 'create');
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const [houseSnap, rateSnap, inviteSnap] = await Promise.all([
      tx.get(home), tx.get(rate), tx.get(invite),
    ]);
    if (!houseSnap.exists || houseSnap.data().ownerUid !== uid) {
      throw new HttpsError('permission-denied', 'Only the household owner can invite.');
    }
    if (inviteSnap.exists) throw new HttpsError('aborted', 'Retry invite generation.');
    applyRateLimit(tx, rate, rateSnap, now, 10);
    tx.create(invite, {
      householdId: home.id,
      createdBy: uid,
      createdAt: FieldValue.serverTimestamp(),
      expiresAt: Timestamp.fromMillis(now + INVITE_TTL_MS),
      status: 'active',
    });
  });
  return { inviteCode: code, householdId: home.id, expiresAtMs: now + INVITE_TTL_MS };
});

export const acceptHouseholdInvite = onCall(options, async (request) => {
  const uid = uidOf(request);
  let normalized;
  try { normalized = normalizeInviteCode(request.data?.inviteCode); }
  catch { throw new HttpsError('invalid-argument', 'Invalid invite format.'); }
  const invite = inviteRef(normalized);
  // Charge every authenticated attempt, including guessed or expired invite codes.
  await chargeRateLimit(uid, 'accept', 10);
  const now = Date.now();
  return db.runTransaction(async (tx) => {
    const inviteSnap = await tx.get(invite);
    if (!inviteSnap.exists) invalidInvite();
    const data = inviteSnap.data();
    const home = houseRef(data.householdId);
    const member = home.collection('members').doc(uid);
    const userIndex = db.doc(`users/${uid}/households/${home.id}`);
    const [homeSnap, memberSnap] = await Promise.all([tx.get(home), tx.get(member)]);
    if (!homeSnap.exists || homeSnap.data().ownerUid !== data.createdBy) invalidInvite();
    // A lost response may be replayed only by the same still-authorized member.
    // Never restore a removed member or redeem a consumed code for a new account.
    if (data.status === 'used') {
      if (data.acceptedBy !== uid || !memberSnap.exists) invalidInvite();
      tx.set(userIndex, {
        householdId: home.id, role: 'member', updatedAt: FieldValue.serverTimestamp(),
      });
      return { householdId: home.id, alreadyMember: true };
    }
    if (data.status !== 'active' || !data.expiresAt ||
      data.expiresAt.toMillis() <= now) invalidInvite();
    if (homeSnap.data().ownerUid === uid || memberSnap.exists) {
      tx.set(userIndex, {
        householdId: home.id,
        role: homeSnap.data().ownerUid === uid ? 'owner' : 'member',
        updatedAt: FieldValue.serverTimestamp(),
      });
      return { householdId: home.id, alreadyMember: true };
    }
    try { requireAvailableMemberSlot(homeSnap.data().memberCount); }
    catch { throw new HttpsError('failed-precondition', 'Household is full.'); }
    tx.create(member, {
      uid, role: 'member', householdId: home.id, joinedAt: FieldValue.serverTimestamp(),
    });
    tx.set(userIndex, {
      householdId: home.id, role: 'member', updatedAt: FieldValue.serverTimestamp(),
    });
    tx.update(home, {
      memberCount: FieldValue.increment(1), updatedAt: FieldValue.serverTimestamp(),
    });
    tx.update(invite, {
      status: 'used', acceptedBy: uid, acceptedAt: FieldValue.serverTimestamp(),
    });
    return { householdId: home.id, alreadyMember: false };
  });
});

export const leaveHousehold = onCall(options, async (request) => {
  const uid = uidOf(request);
  const home = houseRef(request.data?.householdId);
  const member = home.collection('members').doc(uid);
  const index = db.doc(`users/${uid}/households/${home.id}`);
  await db.runTransaction(async (tx) => {
    const [homeSnap, memberSnap] = await Promise.all([tx.get(home), tx.get(member)]);
    if (!homeSnap.exists) throw new HttpsError('not-found', 'Household not found.');
    if (homeSnap.data().ownerUid === uid) {
      throw new HttpsError('failed-precondition', 'Owner must transfer or delete household first.');
    }
    if (!memberSnap.exists) {
      tx.delete(index);
      return;
    }
    try { requireRemovableMemberCount(homeSnap.data().memberCount); }
    catch { throw new HttpsError('failed-precondition', 'Invalid household membership state.'); }
    tx.delete(member);
    tx.delete(index);
    tx.update(home, {
      memberCount: FieldValue.increment(-1), updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { householdId: home.id, left: true };
});

export const removeHouseholdMember = onCall(options, async (request) => {
  const uid = uidOf(request);
  const home = houseRef(request.data?.householdId);
  const target = request.data?.memberUid;
  if (typeof target !== 'string' || target.length < 1 || target.length > 128 || target.includes('/')) {
    throw new HttpsError('invalid-argument', 'Invalid member ID.');
  }
  const member = home.collection('members').doc(target);
  const index = db.doc(`users/${target}/households/${home.id}`);
  await db.runTransaction(async (tx) => {
    const [homeSnap, memberSnap] = await Promise.all([tx.get(home), tx.get(member)]);
    if (!homeSnap.exists || homeSnap.data().ownerUid !== uid) {
      throw new HttpsError('permission-denied', 'Household owner required.');
    }
    if (target === uid) throw new HttpsError('failed-precondition', 'Cannot remove household owner.');
    if (!memberSnap.exists) {
      tx.delete(index);
      return;
    }
    try { requireRemovableMemberCount(homeSnap.data().memberCount); }
    catch { throw new HttpsError('failed-precondition', 'Invalid household membership state.'); }
    tx.delete(member);
    tx.delete(index);
    tx.update(home, {
      memberCount: FieldValue.increment(-1), updatedAt: FieldValue.serverTimestamp(),
    });
  });
  return { householdId: home.id, removed: true };
});
