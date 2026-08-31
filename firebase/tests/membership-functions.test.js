const { after, before, beforeEach, describe, test } = require("node:test");
const assert = require("node:assert/strict");
const admin = require("../functions/node_modules/firebase-admin");
const { createMembershipService } = require("../functions/membership");

const projectId = process.env.GCLOUD_PROJECT || "demo-firestationops";
let app;
let auth;
let firestore;
let service;

const logger = {
  info() {},
  warn() {},
  error() {},
};

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

async function createMember(uid, departmentId, roles, isActive = true) {
  const data = memberDocument(uid, departmentId, roles, isActive);
  await auth.createUser({ uid, email: data.email, password: "Fictional123!" });
  await firestore.doc(`members/${uid}`).set(data);
  await firestore.doc(`departments/${departmentId}/members/${uid}`).set(data);
  return data;
}

function request(uid, data) {
  return { auth: uid ? { uid, token: {} } : null, data };
}

function validProvision(overrides = {}) {
  return {
    email: "new.member@example.test",
    password: "Fictional123!",
    firstName: "New",
    lastName: "Member",
    memberNumber: "101",
    roles: ["MEMBER"],
    isActive: true,
    ...overrides,
  };
}

function validUpdate(targetUserId, overrides = {}) {
  return {
    targetUserId,
    email: `${targetUserId}@example.test`,
    firstName: "Updated",
    lastName: "Member",
    memberNumber: "102",
    roles: ["MEMBER"],
    isActive: true,
    ...overrides,
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

async function rejectsWithCode(operation, code) {
  await assert.rejects(operation, (error) => {
    assert.equal(error.code, code);
    return true;
  });
}

describe("membership callable service", () => {
  before(async () => {
    app = admin.initializeApp({ projectId }, "membership-functions-tests");
    auth = admin.auth(app);
    firestore = admin.firestore(app);
    service = createMembershipService({ auth, firestore, logger, clock: () => 100 });
  });

  beforeEach(clearEmulators);
  after(async () => app.delete());

  test("rejects unauthenticated and non-admin actors", async () => {
    await rejectsWithCode(
      service.provisionDepartmentMember(request(null, validProvision())),
      "unauthenticated",
    );
    await createMember("member-alpha", "dept-alpha", ["MEMBER"]);
    await rejectsWithCode(
      service.provisionDepartmentMember(request("member-alpha", validProvision())),
      "permission-denied",
    );
  });

  test("rejects inactive administrators", async () => {
    await createMember("inactive-admin", "dept-alpha", ["ADMIN"], false);
    await rejectsWithCode(
      service.provisionDepartmentMember(request("inactive-admin", validProvision())),
      "permission-denied",
    );
  });

  test("rejects unknown roles and client-supplied departments", async () => {
    await createMember("admin-alpha", "dept-alpha", ["ADMIN"]);
    await rejectsWithCode(
      service.provisionDepartmentMember(request(
        "admin-alpha",
        validProvision({ roles: ["SUPER_ADMIN"] }),
      )),
      "invalid-argument",
    );
    await rejectsWithCode(
      service.provisionDepartmentMember(request(
        "admin-alpha",
        { ...validProvision(), departmentId: "dept-bravo" },
      )),
      "invalid-argument",
    );
  });

  test("rejects duplicate email addresses", async () => {
    await createMember("admin-alpha", "dept-alpha", ["ADMIN"]);
    await auth.createUser({
      uid: "existing-user",
      email: "duplicate@example.test",
      password: "Fictional123!",
    });
    await rejectsWithCode(
      service.provisionDepartmentMember(request(
        "admin-alpha",
        validProvision({ email: "duplicate@example.test" }),
      )),
      "already-exists",
    );
  });

  test("creates canonical and nested records and server-managed claims", async () => {
    await createMember("admin-alpha", "dept-alpha", ["ADMIN"]);
    const result = await service.provisionDepartmentMember(
      request("admin-alpha", validProvision()),
    );
    const uid = result.member.id;
    assert.equal(result.member.departmentId, "dept-alpha");
    assert.equal(Object.hasOwn(result.member, "password"), false);

    const canonical = await firestore.doc(`members/${uid}`).get();
    const roster = await firestore.doc(`departments/dept-alpha/members/${uid}`).get();
    assert.deepEqual(canonical.data(), roster.data());
    const user = await auth.getUser(uid);
    assert.equal(user.customClaims.departmentId, "dept-alpha");
    assert.deepEqual(user.customClaims.roles, ["MEMBER"]);
    assert.equal(user.customClaims.isActive, true);
  });

  test("prevents removal or deactivation of the final active administrator", async () => {
    await createMember("admin-alpha", "dept-alpha", ["ADMIN"]);
    await rejectsWithCode(
      service.updateDepartmentMember(request(
        "admin-alpha",
        validUpdate("admin-alpha", { email: "admin-alpha@example.test" }),
      )),
      "failed-precondition",
    );
    await rejectsWithCode(
      service.deactivateDepartmentMember(request("admin-alpha", {
        targetUserId: "admin-alpha",
      })),
      "failed-precondition",
    );
  });

  test("rejects cross-department targets", async () => {
    await createMember("admin-alpha", "dept-alpha", ["ADMIN"]);
    await createMember("member-bravo", "dept-bravo", ["MEMBER"]);
    await rejectsWithCode(
      service.updateDepartmentMember(request(
        "admin-alpha",
        validUpdate("member-bravo", { email: "member-bravo@example.test" }),
      )),
      "permission-denied",
    );
    await rejectsWithCode(
      service.deactivateDepartmentMember(request("admin-alpha", {
        targetUserId: "member-bravo",
      })),
      "permission-denied",
    );
  });
});
