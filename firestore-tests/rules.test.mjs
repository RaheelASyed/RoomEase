// RoomEase security rule tests. Run with `npm test` (starts the Firestore emulator for the
// demo-roomease project, runs these tests, shuts it down). Nothing touches a live project.
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import firebase from 'firebase/compat/app';
import 'firebase/compat/firestore';

const { FieldValue, Timestamp } = firebase.firestore;
const now = () => FieldValue.serverTimestamp();

let env;
const HID = 'house1';
const CODE = 'ABC234';

// Accounts: alice = owner, bob = member, olivia = signed-in outsider, nobody = unauthenticated.
const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const olivia = () => env.authenticatedContext('olivia').firestore();
const nobody = () => env.unauthenticatedContext().firestore();

const userDoc = (uid, householdId = null, pendingHouseholdId = null) => ({
  uid, displayName: uid, email: `${uid}@example.com`, householdId, pendingHouseholdId, createdAt: Timestamp.now(),
});

/** Seed: alice owns house1, bob is a member, olivia has an account but no household. */
async function seedHousehold() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc('users/alice').set(userDoc('alice', HID));
    await db.doc('users/bob').set(userDoc('bob', HID));
    await db.doc('users/olivia').set(userDoc('olivia'));
    await db.doc(`households/${HID}`).set({ name: 'Maple', ownerId: 'alice', inviteCode: CODE, createdAt: Timestamp.now() });
    await db.doc(`inviteCodes/${CODE}`).set({ householdId: HID, householdName: 'Maple', ownerId: 'alice', createdAt: Timestamp.now() });
    await db.doc(`households/${HID}/members/alice`).set({ uid: 'alice', displayName: 'Alice', role: 'owner', joinedAt: Timestamp.now() });
    await db.doc(`households/${HID}/members/bob`).set({ uid: 'bob', displayName: 'Bob', role: 'member', joinedAt: Timestamp.now() });
    await db.doc(`households/${HID}/chores/c1`).set({
      title: 'Dishes', assigneeId: 'bob', assigneeName: 'Bob', createdBy: 'alice', createdByName: 'Alice',
      dueDate: Timestamp.now(), done: false, completedAt: null, createdAt: Timestamp.now(),
    });
    await db.doc(`households/${HID}/messages/m1`).set({ text: 'hi', senderId: 'alice', senderName: 'Alice', createdAt: Timestamp.now() });
  });
}

const chore = (overrides = {}) => ({
  title: 'Trash', assigneeId: 'bob', assigneeName: 'Bob', createdBy: 'alice', createdByName: 'Alice',
  dueDate: Timestamp.now(), done: false, completedAt: null, createdAt: now(), ...overrides,
});
const expense = (overrides = {}) => ({
  description: 'Groceries', merchant: 'Kroger', totalCents: 1000, paidBy: 'alice', paidByName: 'Alice',
  participantIds: ['alice', 'bob'], hasReceipt: true, createdAt: now(), ...overrides,
});
const message = (overrides = {}) => ({ text: 'hello', senderId: 'bob', senderName: 'Bob', createdAt: now(), ...overrides });

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-roomease',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => { await env?.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await seedHousehold();
});

describe('unauthenticated users', () => {
  test('cannot read households, members, chores, expenses or messages', async () => {
    const db = nobody();
    await assertFails(db.doc(`households/${HID}`).get());
    await assertFails(db.collection(`households/${HID}/members`).get());
    await assertFails(db.collection(`households/${HID}/chores`).get());
    await assertFails(db.collection(`households/${HID}/expenses`).get());
    await assertFails(db.collection(`households/${HID}/messages`).get());
    await assertFails(db.doc(`inviteCodes/${CODE}`).get());
  });
  test('cannot write anything', async () => {
    await assertFails(nobody().collection(`households/${HID}/messages`).add(message({ senderId: 'bob' })));
    await assertFails(nobody().doc('users/x').set(userDoc('x')));
  });
});

describe('users/{uid}', () => {
  test('owner can create and read only their own doc', async () => {
    await env.clearFirestore();
    await assertSucceeds(olivia().doc('users/olivia').set({ ...userDoc('olivia'), createdAt: now() }));
    await assertSucceeds(olivia().doc('users/olivia').get());
    await assertFails(olivia().doc('users/alice').get());
    await assertFails(olivia().doc('users/mallory').set({ ...userDoc('mallory'), createdAt: now() }));
  });
  test('cannot point householdId at a household without real membership', async () => {
    await assertFails(olivia().doc('users/olivia').update({ householdId: HID }));
  });
  test('cannot list other users', async () => {
    await assertFails(olivia().collection('users').get());
  });
});

describe('household creation (client batch, no Cloud Functions)', () => {
  test('a user without a household can create one atomically', async () => {
    const db = olivia();
    const batch = db.batch();
    batch.set(db.doc('households/h2'), { name: 'Olive House', ownerId: 'olivia', inviteCode: 'XYZ789', createdAt: now() });
    batch.set(db.doc('households/h2/members/olivia'), { uid: 'olivia', displayName: 'Olivia', role: 'owner', joinedAt: now() });
    batch.set(db.doc('inviteCodes/XYZ789'), { householdId: 'h2', householdName: 'Olive House', ownerId: 'olivia', createdAt: now() });
    batch.update(db.doc('users/olivia'), { householdId: 'h2', pendingHouseholdId: null });
    await assertSucceeds(batch.commit());
  });
  test('cannot take over an existing invite code', async () => {
    const db = olivia();
    const batch = db.batch();
    batch.set(db.doc('households/h2'), { name: 'Olive House', ownerId: 'olivia', inviteCode: CODE, createdAt: now() });
    batch.set(db.doc('households/h2/members/olivia'), { uid: 'olivia', displayName: 'Olivia', role: 'owner', joinedAt: now() });
    batch.set(db.doc(`inviteCodes/${CODE}`), { householdId: 'h2', householdName: 'Olive House', ownerId: 'olivia', createdAt: now() });
    batch.update(db.doc('users/olivia'), { householdId: 'h2', pendingHouseholdId: null });
    await assertFails(batch.commit());
  });
  test('a member of one household cannot create a second one', async () => {
    const db = bob();
    const batch = db.batch();
    batch.set(db.doc('households/h3'), { name: 'Second', ownerId: 'bob', inviteCode: 'QRS234', createdAt: now() });
    batch.set(db.doc('households/h3/members/bob'), { uid: 'bob', displayName: 'Bob', role: 'owner', joinedAt: now() });
    batch.set(db.doc('inviteCodes/QRS234'), { householdId: 'h3', householdName: 'Second', ownerId: 'bob', createdAt: now() });
    batch.update(db.doc('users/bob'), { householdId: 'h3' });
    await assertFails(batch.commit());
  });
});

describe('signed-in outsiders', () => {
  test('cannot read the household, roster or any household data', async () => {
    const db = olivia();
    await assertFails(db.doc(`households/${HID}`).get());
    await assertFails(db.collection(`households/${HID}/members`).get());
    await assertFails(db.doc(`households/${HID}/members/alice`).get());
    await assertFails(db.collection(`households/${HID}/chores`).get());
    await assertFails(db.collection(`households/${HID}/expenses`).get());
    await assertFails(db.collection(`households/${HID}/messages`).get());
  });
  test('can check their own (missing) membership doc for routing', async () => {
    await assertSucceeds(olivia().doc(`households/${HID}/members/olivia`).get());
  });
  test('cannot grant themselves membership by guessing the household id', async () => {
    const db = olivia();
    const asMember = db.batch();
    asMember.set(db.doc(`households/${HID}/members/olivia`), { uid: 'olivia', displayName: 'Olivia', role: 'member', joinedAt: now() });
    asMember.update(db.doc('users/olivia'), { householdId: HID });
    await assertFails(asMember.commit());

    const asOwner = db.batch();
    asOwner.set(db.doc(`households/${HID}/members/olivia`), { uid: 'olivia', displayName: 'Olivia', role: 'owner', joinedAt: now() });
    asOwner.update(db.doc('users/olivia'), { householdId: HID });
    await assertFails(asOwner.commit());
  });
  test('cannot post messages, chores or expenses', async () => {
    const db = olivia();
    await assertFails(db.collection(`households/${HID}/messages`).add(message({ senderId: 'olivia', senderName: 'Olivia' })));
    await assertFails(db.collection(`households/${HID}/chores`).add(chore({ createdBy: 'olivia', assigneeId: 'bob' })));
    await assertFails(db.collection(`households/${HID}/expenses`).add(expense({ paidBy: 'olivia', participantIds: ['olivia'] })));
  });
});

describe('owner-approved join flow', () => {
  const request = (overrides = {}) => ({
    uid: 'olivia', displayName: 'Olivia', email: 'olivia@example.com', code: CODE, status: 'pending', createdAt: now(), ...overrides,
  });

  test('full flow: request -> owner approves -> joiner completes', async () => {
    await assertSucceeds(olivia().doc(`inviteCodes/${CODE}`).get());
    await assertSucceeds(olivia().doc(`households/${HID}/joinRequests/olivia`).set(request()));
    await assertSucceeds(alice().collection(`households/${HID}/joinRequests`).where('status', '==', 'pending').get());
    await assertSucceeds(alice().doc(`households/${HID}/joinRequests/olivia`).update({ status: 'approved', decidedBy: 'alice', decidedAt: now() }));

    const db = olivia();
    const batch = db.batch();
    batch.set(db.doc(`households/${HID}/members/olivia`), { uid: 'olivia', displayName: 'Olivia', role: 'member', joinedAt: now() });
    batch.update(db.doc('users/olivia'), { householdId: HID, pendingHouseholdId: null });
    await assertSucceeds(batch.commit());
    await assertSucceeds(db.collection(`households/${HID}/chores`).get());
  });
  test('requester cannot approve their own request', async () => {
    await assertSucceeds(olivia().doc(`households/${HID}/joinRequests/olivia`).set(request()));
    await assertFails(olivia().doc(`households/${HID}/joinRequests/olivia`).update({ status: 'approved', decidedBy: 'olivia', decidedAt: now() }));
    await assertFails(olivia().doc(`households/${HID}/joinRequests/olivia`).set(request({ status: 'approved' })));
  });
  test('cannot create a request already marked approved', async () => {
    await assertFails(olivia().doc(`households/${HID}/joinRequests/olivia`).set(request({ status: 'approved' })));
  });
  test('cannot request with a wrong code or on behalf of someone else', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await ctx.firestore().doc('inviteCodes/OTHER2').set({ householdId: 'elsewhere', householdName: 'X', ownerId: 'z', createdAt: Timestamp.now() });
    });
    await assertFails(olivia().doc(`households/${HID}/joinRequests/olivia`).set(request({ code: 'OTHER2' })));
    await assertFails(olivia().doc(`households/${HID}/joinRequests/mallory`).set(request({ uid: 'mallory' })));
  });
  test('a non-owner member cannot approve requests or list them', async () => {
    await assertSucceeds(olivia().doc(`households/${HID}/joinRequests/olivia`).set(request()));
    await assertFails(bob().doc(`households/${HID}/joinRequests/olivia`).update({ status: 'approved', decidedBy: 'bob', decidedAt: now() }));
    await assertFails(bob().collection(`households/${HID}/joinRequests`).get());
  });
  test('cannot join before approval', async () => {
    await assertSucceeds(olivia().doc(`households/${HID}/joinRequests/olivia`).set(request()));
    const db = olivia();
    const batch = db.batch();
    batch.set(db.doc(`households/${HID}/members/olivia`), { uid: 'olivia', displayName: 'Olivia', role: 'member', joinedAt: now() });
    batch.update(db.doc('users/olivia'), { householdId: HID });
    await assertFails(batch.commit());
  });
  test('members cannot change roles or ownership', async () => {
    await assertFails(bob().doc(`households/${HID}/members/bob`).update({ role: 'owner' }));
    await assertFails(bob().doc(`households/${HID}`).update({ ownerId: 'bob' }));
    await assertFails(alice().doc(`households/${HID}`).update({ ownerId: 'bob' }));
    await assertFails(alice().doc(`households/${HID}/members/bob`).delete());
  });
});

describe('chores', () => {
  test('a member assigns a chore to another member', async () => {
    await assertSucceeds(alice().doc(`households/${HID}/chores/c2`).set(chore()));
  });
  test('cannot assign to a non-member, forge createdBy, or pre-complete', async () => {
    await assertFails(alice().doc(`households/${HID}/chores/c3`).set(chore({ assigneeId: 'olivia' })));
    await assertFails(bob().doc(`households/${HID}/chores/c4`).set(chore({ createdBy: 'alice' })));
    await assertFails(alice().doc(`households/${HID}/chores/c5`).set(chore({ done: true })));
    await assertFails(alice().doc(`households/${HID}/chores/c6`).set(chore({ extra: 'nope' })));
  });
  test('only the assignee can complete it', async () => {
    await assertFails(alice().doc(`households/${HID}/chores/c1`).update({ done: true, completedAt: now() }));
    await assertSucceeds(bob().doc(`households/${HID}/chores/c1`).update({ done: true, completedAt: now() }));
    await assertSucceeds(bob().doc(`households/${HID}/chores/c1`).update({ done: false, completedAt: null }));
  });
  test('assignee cannot change anything else', async () => {
    await assertFails(bob().doc(`households/${HID}/chores/c1`).update({ title: 'Nothing' }));
    await assertFails(bob().doc(`households/${HID}/chores/c1`).update({ assigneeId: 'alice' }));
    await assertFails(bob().doc(`households/${HID}/chores/c1`).update({ createdBy: 'bob' }));
    await assertFails(bob().doc(`households/${HID}/chores/c1`).update({ done: 'yes', completedAt: now() }));
  });
});

describe('expenses', () => {
  test('a member records an expense they paid', async () => {
    await assertSucceeds(alice().doc(`households/${HID}/expenses/e1`).set(expense()));
  });
  test('rejects forged payer, outsiders, fractional cents, duplicates and extra fields', async () => {
    await assertFails(bob().doc(`households/${HID}/expenses/e2`).set(expense({ paidBy: 'alice' })));
    await assertFails(alice().doc(`households/${HID}/expenses/e3`).set(expense({ participantIds: ['alice', 'olivia'] })));
    await assertFails(alice().doc(`households/${HID}/expenses/e4`).set(expense({ totalCents: 10.5 })));
    await assertFails(alice().doc(`households/${HID}/expenses/e5`).set(expense({ totalCents: 0 })));
    await assertFails(alice().doc(`households/${HID}/expenses/e6`).set(expense({ participantIds: ['bob', 'bob'] })));
    await assertFails(alice().doc(`households/${HID}/expenses/e7`).set(expense({ receiptImage: 'base64...' })));
  });
  test('expenses are append-only', async () => {
    await assertSucceeds(alice().doc(`households/${HID}/expenses/e1`).set(expense()));
    await assertFails(alice().doc(`households/${HID}/expenses/e1`).update({ totalCents: 1 }));
    await assertFails(bob().doc(`households/${HID}/expenses/e1`).delete());
  });
});

describe('messages', () => {
  test('members send as themselves and read live', async () => {
    await assertSucceeds(bob().collection(`households/${HID}/messages`).add(message()));
    await assertSucceeds(bob().collection(`households/${HID}/messages`).orderBy('createdAt').limitToLast(200).get());
  });
  test('forged sender ids, empty text and edits are rejected', async () => {
    await assertFails(bob().collection(`households/${HID}/messages`).add(message({ senderId: 'alice' })));
    await assertFails(bob().collection(`households/${HID}/messages`).add(message({ text: '' })));
    await assertFails(bob().collection(`households/${HID}/messages`).add(message({ createdAt: Timestamp.fromMillis(0) })));
    await assertFails(alice().doc(`households/${HID}/messages/m1`).update({ text: 'edited' }));
    await assertFails(alice().doc(`households/${HID}/messages/m1`).delete());
  });
});
