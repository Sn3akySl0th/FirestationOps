const { after, before, beforeEach, describe, test } = require("node:test");
const assert = require("node:assert/strict");
const admin = require("../functions/node_modules/firebase-admin");

const projectId = process.env.GCLOUD_PROJECT || "demo-firestationops";

function memberDocument(uid, departmentId, roles, isActive = true) {
  return {
    id: uid,
    departmentId,
    email: `${uid}@example.test`,
    firstName: "Test",
    lastName: "Member",
    memberNumber: null,
    roles,
    isActive,
    createdAt: 1,
    updatedAt: 1,
  };
}

async function clearEmulators() {
  const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST;
  const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST;
  assert.ok(firestoreHost, "FIRESTORE_EMULATOR_HOST is required");
  assert.ok(authHost, "FIREBASE_AUTH_EMULATOR_HOST is required");
  const firestoreResponse = await fetch(
    `http://${firestoreHost}/emulator/v1/projects/${projectId}/databases/(default)/documents`,
    { method: "DELETE" },
  );
  assert.ok(firestoreResponse.ok, `Unable to clear Firestore emulator: ${firestoreResponse.status}`);
  const authResponse = await fetch(
    `http://${authHost}/emulator/v1/projects/${projectId}/accounts`,
    { method: "DELETE" },
  );
  assert.ok(authResponse.ok, `Unable to clear Auth emulator: ${authResponse.status}`);
}

describe("exported callable membership handlers", () => {
  let app;
  let auth;
  let firestore;
  let handlers;

  before(async () => {
    app = admin.initializeApp({ projectId }, "membership-callable-tests");
    auth = admin.auth(app);
    firestore = admin.firestore(app);
    handlers = require("../functions/index").__testHandlers;
  });

  beforeEach(clearEmulators);
  after(async () => app.delete());

  test("syncMemberClaims callable handler rejects malformed canonical roles", async () => {
    const uid = "malformed-member";
    const member = memberDocument(uid, "dept-alpha", ["ADMIN"]);
    member.roles = "ADMIN";
    await auth.createUser({ uid, email: member.email, password: "Fictional123!" });
    await firestore.doc(`members/${uid}`).set(member);

    const result = await handlers.executeSyncMemberClaims({
      auth: { uid, token: {} },
      data: {},
    });

    assert.equal(result.departmentId, null);
    assert.deepEqual(result.roles, []);
    assert.equal(result.isActive, false);
  });

  test("syncMemberClaims callable handler persists valid canonical claims", async () => {
    const uid = "member-alpha";
    const member = memberDocument(uid, "dept-alpha", ["OFFICER"]);
    await auth.createUser({ uid, email: member.email, password: "Fictional123!" });
    await firestore.doc(`members/${uid}`).set(member);

    const result = await handlers.executeSyncMemberClaims({
      auth: { uid, token: {} },
      data: {},
    });

    assert.equal(result.departmentId, "dept-alpha");
    assert.deepEqual(result.roles, ["OFFICER"]);
    assert.equal(result.isActive, true);

    const user = await auth.getUser(uid);
    assert.equal(user.customClaims.departmentId, "dept-alpha");
    assert.deepEqual(user.customClaims.roles, ["OFFICER"]);
    assert.equal(user.customClaims.isActive, true);
  });
});
