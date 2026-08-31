const admin = require("firebase-admin");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");

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
    const customToken = await admin.auth().createCustomToken(localId);
    return { customToken };
  },
);
