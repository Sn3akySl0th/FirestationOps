# Firebase setup

FirestationOps uses Firebase Authentication, Cloud Firestore, and Cloud Storage for department-scoped sync. Local SQLDelight remains the source of truth for field workflows; sync uploads pending records when connectivity is available.

## Prerequisites

1. Create a Firebase project in the [Firebase console](https://console.firebase.google.com/).
2. Enable **Email/Password** authentication.
3. Create a **Firestore** database and a **Storage** bucket in the same project.
4. Install Firebase CLI if you plan to deploy rules locally:
   ```bash
   npx -y firebase-tools@latest login
   ```

## Android app configuration

1. Add an Android app with package name `com.example.firestationops`.
2. Download `google-services.json` from the Firebase console.
3. Place it at:
   ```
   app/androidApp/google-services.json
   ```
   This file is gitignored. An example template is provided at `app/androidApp/google-services.json.example`.

4. Rebuild the Android app. When `google-services.json` is present, the app uses Firebase Auth and schedules background sync with WorkManager.

Without `google-services.json`, the app continues to use local simulated auth and offline-only persistence.

## Windows desktop configuration

The desktop app uses the [GitLive Firebase Kotlin SDK](https://github.com/GitLiveApp/firebase-kotlin-sdk) on JVM. Register a **Web** app in the Firebase console (or reuse Android app credentials) and create a local config file.

1. Copy `app/desktopApp/firebase-desktop.json.example` to one of:
   - `firebase-desktop.json` in the working directory when launching the desktop app
   - `%USERPROFILE%\.firestationops\firebase.json`
   - Or set `FIRESTATIONOPS_FIREBASE_CONFIG` to the full path of your JSON file
2. Fill in `projectId`, `apiKey`, `applicationId`, and `storageBucket` from the Firebase console.
3. Rebuild and run the desktop app. When the config file is present, the app uses Firebase Auth and sync on sign-in and when you tap **Sync now**.

Without a desktop Firebase config file, the desktop app continues to use local simulated auth and offline-only persistence.

Cloud member provisioning remains Android-only. When Firebase is configured, desktop roster controls are disabled with an explanation so a local edit cannot be mistaken for a successful cloud change.

## Member provisioning

Each signed-in user must have a profile document:

```
members/{firebaseUid}
  departmentId: "5"           # fire department number (tenant)
  memberNumber: "221"         # firefighter badge number (200-225)
  email: "member@example.com"
  firstName: "Chris"
  lastName: "Lefebvre"
  roles: ["ADMIN"]
  isActive: true
```

### Server-controlled roster management

Administrators add members from **Department settings** on the officer dashboard:

1. Open **Department settings** from the dashboard (officers and admins).
2. Tap **Add member** (admins only).
3. Enter email, name, optional badge number, roles, and an **initial password** (at least 6 characters).
4. Save. Android calls `provisionDepartmentMember`; the callable Function verifies the acting administrator, creates the Authentication account, and atomically creates the canonical and nested roster records.

The client does not create Firebase Authentication accounts or membership documents directly. It does not save the new member locally until the callable Function succeeds, and it clears the submitted password from UI state.

For roster-only entries without app sign-in, use local development mode (without `google-services.json`).

All department data lives under `departments/5/...` (stations, apparatus, inspections, etc.).

Legacy department identifiers are not normalized by clients. Users without a valid canonical `members/{uid}` document cannot obtain department access. Administrators can bootstrap an empty cloud catalog from **Department settings**, but catalog bootstrap does not upload or alter member records.

The callable Functions derive the department from the acting administrator's canonical record. They reject client-supplied department authority, validate roles and profile fields, protect the final active administrator, update custom claims through the Admin SDK, and revoke refresh tokens after authority changes.

## Department catalog paths

```
departments/{departmentId}
departments/{departmentId}/stations/{stationId}
departments/{departmentId}/apparatus/{apparatusId}
departments/{departmentId}/templates/{templateId}
departments/{departmentId}/members/{memberId}
```

Catalog records are downloaded on sync and stored locally in SQLDelight. Operational records continue to use the paths documented in Milestone 10.

## Deploy security rules

From the repository root:

```bash
npx -y firebase-tools@latest deploy --only firestore:rules,storage
```

Review `firebase/firestore.rules` and `firebase/storage.rules` before deploying to production.

Do not deploy Functions or tightened rules until the migration checklist below is complete and emulator tests pass. Deploying rules before canonical records are normalized can lock out legitimate users.

## Safe initial administrator bootstrap

Roster callables require an existing active administrator. Bootstrap the first administrator only from a trusted environment using the Admin SDK and Application Default Credentials:

1. Verify the exact Firebase project and create the initial Authentication user.
2. In one Firestore transaction, write matching `members/{uid}` and `departments/{departmentId}/members/{uid}` documents with the Auth UID, exact `departmentId`, `roles: ["ADMIN"]`, and `isActive: true`.
3. Set `departmentId`, `roles`, and `isActive` custom claims through the Admin SDK.
4. Verify both documents and active-admin coverage, then sign out and sign back in before calling roster Functions.

Never open rules temporarily, self-create membership from a client, or commit service-account credentials to bootstrap an administrator. See `docs/permissions.md` for the complete process.

## Required migration checklist

Complete every item before production deployment:

- [ ] Normalize every canonical `members/{uid}` record so `id` equals the Firebase Auth UID and `departmentId` is the exact tenant. Move legacy badge numbers from `departmentId` to `memberNumber` where applicable.
- [ ] Add a reviewed Boolean `isActive` to records where it is missing; do not automatically activate unknown accounts.
- [ ] Replace malformed, scalar, empty, misspelled, or unknown roles with a reviewed non-empty list containing only `MEMBER`, `APPARATUS_OFFICER`, `OFFICER`, or `ADMIN`.
- [ ] Reconcile canonical records with `departments/{departmentId}/members/{uid}` projections and remove or archive stale pending/invite projections through trusted server tooling.
- [ ] Backfill or correct missing/mismatched `departmentId` values on every department-scoped document, including nested command-log and assignment records.
- [ ] Verify every department has at least one active canonical `ADMIN`; two active administrators are recommended during migration.
- [ ] After role or active-state changes, update claims through server code, revoke refresh tokens, and have affected users force-refresh or sign in again. Firestore and Storage use canonical membership immediately, while application-visible claims require a new ID token.

Take a reviewed backup/export and define a rollback procedure before normalizing production data. Do not use production member or incident data in emulator fixtures.

## Verification plan

1. Sign in with a Firebase user that has a `members/{uid}` document.
2. Complete and submit an inspection while offline.
3. Reconnect and tap **Sync now** on the dashboard.
4. Confirm the inspection document appears under `departments/{departmentId}/inspections/{inspectionId}`.
5. Attach a photo to a failed item, submit, sync, and confirm the Storage object and attachment metadata upload.

## Emulator testing

```bash
npm --prefix firebase/functions ci
npm --prefix firebase/tests ci
firebase emulators:exec --project demo-firestationops --only auth,firestore,storage "npm --prefix firebase/tests test"
```

The Firebase CLI currently requires Java 21 or newer. The tests use only fictional `dept-alpha` and `dept-bravo` data. Point the Android app at emulators during development if desired (requires additional Android emulator host configuration).

## Android physical device login troubleshooting

Sideloaded debug builds on some phones (especially Android 14+) can fail Firebase SDK app verification (Play Integrity + reCAPTCHA) even when email/password are correct. Symptoms: login spinner or timeout.

### Required Firebase console setup

1. Add **SHA-1 and SHA-256** for your debug keystore in Firebase project settings.
2. Re-download `google-services.json` and rebuild the app.
3. Enable the **Play Integrity API** for the project in [Google Cloud Console](https://console.cloud.google.com/apis/library/playintegrity.googleapis.com?project=firestationops).

Get local debug fingerprints:

```bash
./gradlew :app:androidApp:signingReport
```

### App Check setup (required for Storage uploads in debug builds)

Photo uploads fail with **"User does not have permission to access this object"** when Cloud Storage enforces App Check but the device cannot obtain a valid App Check token.

**Step 1 — Enable the Firebase App Check API** (one-time per project):

1. Open [Google Cloud → Firebase App Check API](https://console.cloud.google.com/apis/library/firebaseappcheck.googleapis.com?project=firestationops).
2. Click **Enable** and wait a minute for propagation.

**Step 2 — Register the debug secret** (once per debug install / emulator):

1. Run the debug app once on the device.
2. In logcat, find the debug secret (either tag works):
   - `FirestationOpsFirebase` — `App Check debug secret: ...`
   - `DebugAppCheckProvider` — `Enter this debug secret into the allow list ...`
3. In [Firebase Console → App Check](https://console.firebase.google.com/project/firestationops/appcheck), open the Android app → **Manage debug tokens** → add that secret (UUID format, not a long JWT).

**Step 3 — Retry upload** after cloud login (not offline sign-in).

Until the API is enabled and the debug secret is registered, Firestore sync may work while Storage uploads fail.

### Custom token fallback (recommended for physical devices)

When SDK sign-in times out, the app can call a Cloud Function that verifies credentials server-side and returns a custom token (bypasses Play Integrity on the device).

1. Store the Firebase Web API key as a function secret (same key as in `google-services.json`):

   ```bash
   npx -y firebase-tools@latest functions:secrets:set IDENTITY_TOOLKIT_API_KEY
   ```

2. Deploy the function:

   ```bash
   cd firebase/functions
   npm install
   cd ../..
   npx -y firebase-tools@latest deploy --only functions:issueCustomToken
   ```

3. Rebuild/install the Android app and try **Login** again.

### Offline sign-in

Use **Sign in offline (recommended on this device)** for local-only testing when cloud sign-in is blocked. Sync will not run until Firebase authentication succeeds.
