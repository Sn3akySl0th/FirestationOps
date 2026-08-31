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

function membership(uid, departmentId, isActive = true) {
  return {
    id: uid,
    departmentId,
    email: `${uid}@example.test`,
    firstName: "Test",
    lastName: "Member",
    memberNumber: null,
    roles: ["MEMBER"],
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
    await setDoc(doc(db, "members/inactive-alpha"), membership("inactive-alpha", "dept-alpha", false));
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
});
