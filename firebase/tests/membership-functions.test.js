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
    await rejectsWithCode(
      service.provisionDepartmentMember(request(
        "admin-alpha",
        validProvision({ roles: [] }),
      )),
      "invalid-argument",
    );
    await rejectsWithCode(
      service.provisionDepartmentMember(request(
        "admin-alpha",
        validProvision({ roles: ["ADMIN", "UNKNOWN"] }),
      )),
      "invalid-argument",
    );
  });

  test("rejects actors with empty, unknown, mixed, or malformed canonical roles", async () => {
    await createMember("empty-roles-admin", "dept-alpha", []);
    await rejectsWithCode(
      service.provisionDepartmentMember(request("empty-roles-admin", validProvision())),
      "permission-denied",
    );

    await createMember("unknown-role-admin", "dept-alpha", ["UNKNOWN"]);
    await rejectsWithCode(
      service.provisionDepartmentMember(request("unknown-role-admin", validProvision())),
      "permission-denied",
    );

    await createMember("mixed-role-admin", "dept-alpha", ["ADMIN", "UNKNOWN"]);
    await rejectsWithCode(
      service.provisionDepartmentMember(request("mixed-role-admin", validProvision())),
      "permission-denied",
    );

    const malformed = memberDocument("malformed-admin", "dept-alpha", ["ADMIN"]);
    malformed.roles = "ADMIN";
    await auth.createUser({ uid: malformed.id, email: malformed.email, password: "Fictional123!" });
    await firestore.doc(`members/${malformed.id}`).set(malformed);
    await firestore.doc(`departments/dept-alpha/members/${malformed.id}`).set(malformed);
    await rejectsWithCode(
      service.provisionDepartmentMember(request("malformed-admin", validProvision())),
      "permission-denied",
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

  test("update synchronizes claims and revokes refresh tokens when authority changes", async () => {
    await createMember("admin-alpha", "dept-alpha", ["ADMIN"]);
    await createMember("member-alpha", "dept-alpha", ["MEMBER"]);
    await createMember("admin-beta", "dept-alpha", ["ADMIN"]);

    await service.updateDepartmentMember(request(
      "admin-alpha",
      validUpdate("member-alpha", {
        email: "member-alpha@example.test",
        roles: ["OFFICER"],
      }),
    ));

    const user = await auth.getUser("member-alpha");
    assert.equal(user.customClaims.departmentId, "dept-alpha");
    assert.deepEqual(user.customClaims.roles, ["OFFICER"]);
    assert.equal(user.customClaims.isActive, true);
  });

  test("deactivation synchronizes claims and clears active authority", async () => {
    await createMember("admin-alpha", "dept-alpha", ["ADMIN"]);
    await createMember("member-alpha", "dept-alpha", ["MEMBER"]);
    await createMember("admin-beta", "dept-alpha", ["ADMIN"]);

    await service.deactivateDepartmentMember(request("admin-alpha", {
      targetUserId: "member-alpha",
    }));

    const user = await auth.getUser("member-alpha");
    assert.equal(user.customClaims.isActive, false);
    assert.deepEqual(user.customClaims.roles, ["MEMBER"]);
  });

  test("rolls back membership writes when claims synchronization fails after update", async () => {
    await createMember("admin-alpha", "dept-alpha", ["ADMIN"]);
    await createMember("member-alpha", "dept-alpha", ["MEMBER"]);
    await createMember("admin-beta", "dept-alpha", ["ADMIN"]);

    const failingService = createMembershipService({
      auth: {
        ...auth,
        async getUser(uid) {
          return auth.getUser(uid);
        },
        async setCustomUserClaims() {
          throw Object.assign(new Error("claims sync failed"), { code: "auth/internal-error" });
        },
        async revokeRefreshTokens(uid) {
          return auth.revokeRefreshTokens(uid);
        },
        async createUser(user) {
          return auth.createUser(user);
        },
        async updateUser(uid, updates) {
          return auth.updateUser(uid, updates);
        },
        async getUserByEmail(email) {
          return auth.getUserByEmail(email);
        },
        async deleteUser(uid) {
          return auth.deleteUser(uid);
        },
      },
      firestore,
      logger,
      clock: () => 200,
    });

    await rejectsWithCode(
      failingService.updateDepartmentMember(request(
        "admin-alpha",
        validUpdate("member-alpha", {
          email: "member-alpha@example.test",
          roles: ["OFFICER"],
        }),
      )),
      "aborted",
    );

    const canonical = await firestore.doc("members/member-alpha").get();
    assert.ok(canonical.exists);
    assert.deepEqual(canonical.data().roles, ["MEMBER"]);
  });
});
