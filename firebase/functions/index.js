const admin = require("firebase-admin");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { logger } = require("firebase-functions");
const { ALLOWED_ROLES, createMembershipService } = require("./membership");

admin.initializeApp();

const identityToolkitApiKey = defineSecret("IDENTITY_TOOLKIT_API_KEY");

async function signInWithPassword(apiKey, email, password) {
  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email,
        password,
        returnSecureToken: true,
      }),
    },
  );

  const body = await response.json();
  if (!response.ok) {
    const message = body?.error?.message || "Firebase REST sign-in failed.";
    throw new HttpsError("permission-denied", message);
  }

  return body.localId;
}

async function departmentClaimForUser(localId) {
  const memberDoc = await admin.firestore().doc(`members/${localId}`).get();
  if (!memberDoc.exists) {
    return null;
  }

  const departmentId = memberDoc.data()?.departmentId;
  const roles = memberDoc.data()?.roles;
  const isActive = memberDoc.data()?.isActive;
  if (memberDoc.data()?.id !== localId ||
      typeof departmentId !== "string" ||
      departmentId.length === 0 ||
      !Array.isArray(roles) ||
      roles.length === 0 ||
      roles.some((role) => !ALLOWED_ROLES.has(role)) ||
      typeof isActive !== "boolean") {
    return null;
  }
  return { departmentId, roles, isActive };
}

async function persistDepartmentClaims(localId) {
  const claims = await departmentClaimForUser(localId);
  if (!claims) {
    await admin.auth().setCustomUserClaims(localId, {});
    return null;
  }
  const user = await admin.auth().getUser(localId);
  const existingClaims = { ...(user.customClaims || {}) };
  delete existingClaims.departmentId;
  delete existingClaims.roles;
  delete existingClaims.isActive;
  await admin.auth().setCustomUserClaims(localId, { ...existingClaims, ...claims });
  return claims;
}

const membershipService = createMembershipService({
  auth: admin.auth(),
  firestore: admin.firestore(),
  logger,
});

exports.issueCustomToken = onCall(
  {
    secrets: [identityToolkitApiKey],
    region: "us-central1",
  },
  async (request) => {
    const email = String(request.data?.email || "").trim().toLowerCase();
    const password = String(request.data?.password || "");
    if (!email || !password) {
      throw new HttpsError("invalid-argument", "Email and password are required.");
    }

    const localId = await signInWithPassword(identityToolkitApiKey.value(), email, password);
    const claims = await persistDepartmentClaims(localId);
    if (!claims || claims.isActive !== true) {
      throw new HttpsError("permission-denied", "Active department membership required.");
    }
    const customToken = await admin.auth().createCustomToken(localId);
    return { customToken };
  },
);

exports.syncMemberClaims = onCall(
  {
    region: "us-central1",
  },
  async (request) => {
    if (!request.auth?.uid) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }

    const claims = await persistDepartmentClaims(request.auth.uid);
    return {
      departmentId: claims?.departmentId ?? null,
      roles: claims?.roles ?? [],
      isActive: claims?.isActive ?? false,
    };
  },
);

exports.provisionDepartmentMember = onCall(
  { region: "us-central1" },
  membershipService.provisionDepartmentMember,
);

exports.updateDepartmentMember = onCall(
  { region: "us-central1" },
  membershipService.updateDepartmentMember,
);

exports.deactivateDepartmentMember = onCall(
  { region: "us-central1" },
  membershipService.deactivateDepartmentMember,
);
