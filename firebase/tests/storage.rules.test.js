const { after, before, beforeEach, describe, test } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const { doc, setDoc } = require("firebase/firestore");
const { ref, uploadBytes } = require("firebase/storage");

const projectId = process.env.GCLOUD_PROJECT || "demo-firestationops";
let testEnv;

function membership(uid, departmentId, roles = ["MEMBER"], isActive = true) {
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

async function seedMemberships() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "members/member-alpha"), membership("member-alpha", "dept-alpha"));
    await setDoc(doc(db, "members/member-bravo"), membership("member-bravo", "dept-bravo"));
    await setDoc(doc(db, "members/inactive-alpha"), membership("inactive-alpha", "dept-alpha", ["MEMBER"], false));
  });
}

function storageFor(uid) {
  return testEnv.authenticatedContext(uid, { email: `${uid}@example.test` }).storage();
}

function upload(storage, objectPath, size, contentType) {
  return uploadBytes(ref(storage, objectPath), new Uint8Array(size), { contentType });
}

describe("Storage department isolation", () => {
  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId,
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, "../firestore.rules"), "utf8"),
      },
      storage: {
        rules: fs.readFileSync(path.resolve(__dirname, "../storage.rules"), "utf8"),
      },
    });
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await testEnv.clearStorage();
    await seedMemberships();
  });

  after(async () => testEnv.cleanup());

  test("valid same-department image upload is allowed", async () => {
    await assertSucceeds(upload(
      storageFor("member-alpha"),
      "departments/dept-alpha/attachments/photo-1.jpg",
      1024,
      "image/jpeg",
    ));
  });

  test("invalid MIME type and oversized uploads are denied", async () => {
    const storage = storageFor("member-alpha");
    await assertFails(upload(
      storage,
      "departments/dept-alpha/attachments/document.pdf",
      1024,
      "application/pdf",
    ));
    await assertFails(upload(
      storage,
      "departments/dept-alpha/attachments/too-large.jpg",
      10 * 1024 * 1024,
      "image/jpeg",
    ));
  });

  test("cross-tenant, inactive, missing-membership, and unauthenticated uploads are denied", async () => {
    await assertFails(upload(
      storageFor("member-alpha"),
      "departments/dept-bravo/attachments/cross-tenant.jpg",
      1024,
      "image/jpeg",
    ));
    await assertFails(upload(
      storageFor("inactive-alpha"),
      "departments/dept-alpha/attachments/inactive.jpg",
      1024,
      "image/jpeg",
    ));
    await assertFails(upload(
      storageFor("no-membership"),
      "departments/dept-alpha/attachments/no-membership.jpg",
      1024,
      "image/jpeg",
    ));
    await assertFails(upload(
      testEnv.unauthenticatedContext().storage(),
      "departments/dept-alpha/attachments/unauthenticated.jpg",
      1024,
      "image/jpeg",
    ));
  });

  test("malformed or invalid canonical roles are denied for storage uploads", async () => {
    await testEnv.withSecurityRulesDisabled(async (context) => {
      const db = context.firestore();
      const invalidMemberships = [
        ["empty-roles", "dept-alpha", [], true],
        ["unknown-role", "dept-alpha", ["UNKNOWN"], true],
        ["mixed-role", "dept-alpha", ["ADMIN", "UNKNOWN"], true],
        ["scalar-role", "dept-alpha", "ADMIN", true],
      ];
      for (const [uid, departmentId, roles, isActive] of invalidMemberships) {
        const data = membership(uid, departmentId, roles, isActive);
        await setDoc(doc(db, `members/${uid}`), data);
      }
    });

    for (const uid of ["empty-roles", "unknown-role", "mixed-role", "scalar-role"]) {
      await assertFails(upload(
        storageFor(uid),
        "departments/dept-alpha/attachments/invalid-role.jpg",
        1024,
        "image/jpeg",
      ));
    }
  });
});
