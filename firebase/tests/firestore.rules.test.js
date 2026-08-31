const { after, before, beforeEach, describe, test } = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} = require("@firebase/rules-unit-testing");
const { deleteDoc, doc, getDoc, setDoc, updateDoc } = require("firebase/firestore");

const projectId = process.env.GCLOUD_PROJECT || "demo-firestationops";
let testEnv;

const identities = {
  memberAlpha: ["member-alpha", "dept-alpha", ["MEMBER"], true],
  apparatusOfficerAlpha: ["apparatus-alpha", "dept-alpha", ["APPARATUS_OFFICER"], true],
  officerAlpha: ["officer-alpha", "dept-alpha", ["OFFICER"], true],
  adminAlpha: ["admin-alpha", "dept-alpha", ["ADMIN"], true],
  memberBravo: ["member-bravo", "dept-bravo", ["MEMBER"], true],
  adminBravo: ["admin-bravo", "dept-bravo", ["ADMIN"], true],
  inactiveAlpha: ["inactive-alpha", "dept-alpha", ["MEMBER"], false],
};

function membership(uid, departmentId, roles, isActive) {
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

async function seed() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    for (const [uid, departmentId, roles, isActive] of Object.values(identities)) {
      const data = membership(uid, departmentId, roles, isActive);
      await setDoc(doc(db, `members/${uid}`), data);
      await setDoc(doc(db, `departments/${departmentId}/members/${uid}`), data);
    }
    await setDoc(doc(db, "departments/dept-alpha"), {
      id: "dept-alpha",
      departmentId: "dept-alpha",
      name: "Alpha Volunteer Fire Department",
      stationIds: [],
      createdAt: 1,
      updatedAt: 1,
    });
    await setDoc(doc(db, "departments/dept-bravo"), {
      id: "dept-bravo",
      departmentId: "dept-bravo",
      name: "Bravo Volunteer Fire Department",
      stationIds: [],
      createdAt: 1,
      updatedAt: 1,
    });
    await setDoc(doc(db, "departments/dept-alpha/deficiencies/deficiency-1"), {
      id: "deficiency-1",
      departmentId: "dept-alpha",
      apparatusId: "engine-1",
      title: "Damaged hand light",
      description: "Fictional test deficiency",
      severity: "REPAIR_NEEDED",
      status: "OPEN",
      createdAt: 10,
      createdByUserId: "member-alpha",
      resolvedAt: null,
      resolvedByUserId: null,
      resolutionNote: null,
      syncStatus: "SYNCED",
      attachmentIds: [],
    });
    await setDoc(doc(db, "departments/dept-alpha/inspections/finalized-1"), {
      id: "finalized-1",
      departmentId: "dept-alpha",
      templateId: "template-1",
      apparatusId: "engine-1",
      startedAt: 10,
      completedAt: 20,
      startedByUserId: "member-alpha",
      responsesJson: "[]",
      isFinalized: true,
      syncStatus: "SYNCED",
      voidedAt: null,
      voidedReason: null,
    });
    await setDoc(doc(db, "departments/dept-alpha/incidents/incident-1"), {
      id: "incident-1",
      departmentId: "dept-alpha",
      title: "Fictional training incident",
      summary: "",
      locationDescription: "Training grounds",
      incidentType: "TRAINING",
      status: "DRAFT",
      createdAt: 10,
      createdByUserId: "member-alpha",
      updatedAt: 10,
      updatedByUserId: "member-alpha",
      closedAt: null,
      closedByUserId: null,
      syncStatus: "SYNCED",
    });
    await setDoc(doc(db, "departments/dept-alpha/incidents/incident-1/commandLog/log-1"), {
      id: "log-1",
      incidentId: "incident-1",
      departmentId: "dept-alpha",
      message: "Training command established",
      entryType: "LOG",
      createdAt: 11,
      createdByUserId: "member-alpha",
      incidentTimestamp: null,
      correctsEntryId: null,
      syncStatus: "SYNCED",
    });
  });
}

function dbFor(uid) {
  return testEnv.authenticatedContext(uid, { email: `${uid}@example.test` }).firestore();
}

describe("Firestore department isolation", () => {
  before(async () => {
    testEnv = await initializeTestEnvironment({
      projectId,
      firestore: {
        rules: fs.readFileSync(path.resolve(__dirname, "../firestore.rules"), "utf8"),
      },
    });
  });

  beforeEach(async () => {
    await testEnv.clearFirestore();
    await seed();
  });

  after(async () => testEnv.cleanup());

  test("unauthenticated, missing-membership, and inactive users are denied", async () => {
    await assertFails(getDoc(doc(testEnv.unauthenticatedContext().firestore(), "departments/dept-alpha")));
    await assertFails(getDoc(doc(dbFor("no-membership"), "departments/dept-alpha")));
    await assertFails(getDoc(doc(dbFor("inactive-alpha"), "departments/dept-alpha")));
  });

  test("cross-department reads and writes are denied", async () => {
    const db = dbFor("member-alpha");
    await assertFails(getDoc(doc(db, "departments/dept-bravo")));
    await assertFails(setDoc(doc(db, "departments/dept-bravo/incidents/malicious-incident"), {
      id: "malicious-incident",
      departmentId: "dept-bravo",
      title: "Cross tenant write",
      summary: "",
      locationDescription: "",
      incidentType: "OTHER",
      status: "DRAFT",
      createdAt: 20,
      createdByUserId: "member-alpha",
      updatedAt: 20,
      updatedByUserId: "member-alpha",
      closedAt: null,
      closedByUserId: null,
      syncStatus: "PENDING_SYNC",
    }));
  });

  test("clients cannot create or change canonical membership", async () => {
    const memberDb = dbFor("member-alpha");
    await assertFails(setDoc(doc(memberDb, "members/malicious-user"),
      membership("malicious-user", "dept-alpha", ["ADMIN"], true)));
    await assertFails(updateDoc(doc(memberDb, "members/member-alpha"), {
      roles: ["ADMIN"],
      departmentId: "dept-bravo",
    }));
  });

  test("clients can read only their own canonical membership", async () => {
    const db = dbFor("member-alpha");
    await assertSucceeds(getDoc(doc(db, "members/member-alpha")));
    await assertFails(getDoc(doc(db, "members/officer-alpha")));
  });

  test("an admin cannot manage canonical or nested membership from a client", async () => {
    const db = dbFor("admin-alpha");
    await assertFails(updateDoc(doc(db, "members/member-bravo"), { roles: ["ADMIN"] }));
    await assertFails(setDoc(
      doc(db, "departments/dept-bravo/members/malicious-user"),
      membership("malicious-user", "dept-bravo", ["ADMIN"], true),
    ));
  });

  test("embedded department mismatch is denied", async () => {
    const db = dbFor("admin-alpha");
    await assertFails(setDoc(doc(db, "departments/dept-alpha/stations/station-bad"), {
      id: "station-bad",
      departmentId: "dept-bravo",
      name: "Mismatched Station",
      address: null,
      createdAt: 1,
      updatedAt: 1,
    }));
  });

  test("finalized inspections cannot be updated", async () => {
    await assertFails(updateDoc(
      doc(dbFor("member-alpha"), "departments/dept-alpha/inspections/finalized-1"),
      { responsesJson: "[{}]" },
    ));
  });

  test("command log entries are append-only", async () => {
    const ref = doc(dbFor("officer-alpha"),
      "departments/dept-alpha/incidents/incident-1/commandLog/log-1");
    await assertFails(updateDoc(ref, { message: "Silently rewritten" }));
    await assertFails(deleteDoc(ref));
  });

  test("regular members can append command log entries", async () => {
    await assertSucceeds(setDoc(
      doc(dbFor("member-alpha"),
        "departments/dept-alpha/incidents/incident-1/commandLog/log-2"),
      {
        id: "log-2",
        incidentId: "incident-1",
        departmentId: "dept-alpha",
        message: "Fictional status update",
        entryType: "LOG",
        createdAt: 12,
        createdByUserId: "member-alpha",
        incidentTimestamp: null,
        correctsEntryId: null,
        syncStatus: "PENDING_SYNC",
      },
    ));
  });

  test("regular members cannot resolve deficiencies", async () => {
    await assertFails(updateDoc(
      doc(dbFor("member-alpha"), "departments/dept-alpha/deficiencies/deficiency-1"),
      {
        status: "RESOLVED",
        resolvedAt: 30,
        resolvedByUserId: "member-alpha",
        resolutionNote: "Fictional resolution",
      },
    ));
  });

  test("same-department apparatus officers can resolve deficiencies", async () => {
    await assertSucceeds(updateDoc(
      doc(dbFor("apparatus-alpha"), "departments/dept-alpha/deficiencies/deficiency-1"),
      {
        status: "RESOLVED",
        resolvedAt: 30,
        resolvedByUserId: "apparatus-alpha",
        resolutionNote: "Fictional repair completed",
      },
    ));
  });

  test("same-department officers can update incidents", async () => {
    await assertSucceeds(updateDoc(
      doc(dbFor("officer-alpha"), "departments/dept-alpha/incidents/incident-1"),
      {
        status: "ACTIVE",
        updatedAt: 30,
        updatedByUserId: "officer-alpha",
      },
    ));
  });

  test("same-department admins can manage catalog records", async () => {
    await assertSucceeds(setDoc(
      doc(dbFor("admin-alpha"), "departments/dept-alpha/stations/station-1"),
      {
        id: "station-1",
        departmentId: "dept-alpha",
        name: "Alpha Station One",
        address: null,
        createdAt: 1,
        updatedAt: 1,
      },
    ));
  });

  test("malformed or invalid canonical roles are denied", async () => {
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
        await setDoc(doc(db, `departments/${departmentId}/members/${uid}`), data);
      }
    });

    for (const uid of ["empty-roles", "unknown-role", "mixed-role", "scalar-role"]) {
      await assertFails(getDoc(doc(dbFor(uid), "departments/dept-alpha")));
    }
  });

  test("valid canonical roles still authorize department access", async () => {
    await assertSucceeds(getDoc(doc(dbFor("member-alpha"), "departments/dept-alpha")));
    await assertSucceeds(getDoc(doc(dbFor("apparatus-alpha"), "departments/dept-alpha")));
    await assertSucceeds(getDoc(doc(dbFor("officer-alpha"), "departments/dept-alpha")));
    await assertSucceeds(getDoc(doc(dbFor("admin-alpha"), "departments/dept-alpha")));
  });
});
