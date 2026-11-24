import { createHash } from 'node:crypto';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { rateLimitNext } from './invite-core.js';
import { normalizeSharedHouseholdName, sharedHouseholdId } from './household-create-core.js';

export const createSharedHousehold = onCall(
  { region: 'asia-northeast3', maxInstances: 10, timeoutSeconds: 30 },
  async (request) => {
    const uid = request.auth?.uid;
    if (!uid) throw new HttpsError('unauthenticated', 'Firebase account is required.');
    let name, householdId;
    try {
      name = normalizeSharedHouseholdName(request.data?.name);
      householdId = sharedHouseholdId(uid, request.data?.requestId);
    } catch {
      throw new HttpsError('invalid-argument', 'Invalid household request.');
    }
    const db = getFirestore();
    const home = db.doc(`households/${householdId}`);
    const index = db.doc(`users/${uid}/households/${householdId}`);
    const rateKey = createHash('sha256').update(uid).digest('hex');
    const rate = db.doc('inviteRateLimits/house-create-' + rateKey);
    return db.runTransaction(async tx => {
      const existing = await tx.get(home);
      if (existing.exists) {
        if (existing.data().ownerUid !== uid) {
          throw new HttpsError('permission-denied', 'Household owner required.');
        }
      } else {
        const previous = await tx.get(rate);
        let next;
        try { next = rateLimitNext(previous.data(), Date.now(), 10); }
        catch { throw new HttpsError('resource-exhausted', 'Too many requests.'); }
        tx.set(rate, next, { merge: true });
        tx.create(home, {
          name, ownerUid: uid, schemaVersion: 1, memberCount: 1,
          createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
        });
      }
      tx.set(index, {
        householdId, role: 'owner', updatedAt: FieldValue.serverTimestamp(),
      });
      return { householdId };
    });
  }
);
