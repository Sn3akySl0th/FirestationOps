const admin = require("firebase-admin");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

admin.initializeApp();

const identityToolkitApiKey = defineSecret("IDENTITY_TOOLKIT_API_KEY");

function normalizeDepartmentId(departmentId) {
  if (typeof departmentId !== "string") {
    return null;
  }
  const parsed = Number.parseInt(departmentId, 10);
  if (!Number.isNaN(parsed) && parsed >= 200 && parsed <= 225) {
    return "5";
  }
  return departmentId;
}

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
    return {};
  }

  const departmentId = normalizeDepartmentId(memberDoc.data()?.departmentId);
  return departmentId ? { departmentId } : {};
}

async function persistDepartmentClaims(localId) {
  const claims = await departmentClaimForUser(localId);
  await admin.auth().setCustomUserClaims(localId, claims);
  return claims;
}

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
    const customToken = await admin.auth().createCustomToken(localId, claims);
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
    return { departmentId: claims.departmentId ?? null };
  },
);
