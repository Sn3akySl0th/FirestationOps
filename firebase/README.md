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

Member account creation (initial password provisioning) remains Android-only. Desktop can manage rosters locally and sync existing cloud members after sign-in.

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

### In-app roster management (Milestone 12)

Administrators add members from **Department settings** on the officer dashboard:

1. Open **Department settings** from the dashboard (officers and admins).
2. Tap **Add member** (admins only).
3. Enter email, name, optional badge number, roles, and an **initial password** (at least 6 characters).
4. Save — the app creates the Firebase Authentication account and member profile in one step.

The member can sign in immediately with that email and password. No Firebase console step is required.

For roster-only entries without app sign-in, use local development mode (without `google-services.json`).

All department data lives under `departments/5/...` (stations, apparatus, inspections, etc.).

If `departmentId` was previously set to a badge number like `221`, the app remaps it to department `5` and stores `221` in `memberNumber` on sign-in.

Users without a `members/{uid}` document cannot sign in unless their email matches a locally seeded development member (first-time bootstrap only). The app does not assign `mock-dept-id` to unknown Firebase users.

On first Firebase sign-in with a matching local seeded member, the app creates the `members/{uid}` document and mirrors it to `departments/{departmentId}/members/{uid}`.

Administrators can bootstrap an empty cloud catalog from **Department settings** on the officer dashboard. This uploads the demo stations, apparatus, templates, and member roster for the department.

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

**Important:** After pulling roster-management changes, deploy updated Firestore rules before adding members from the app:

```bash
npx -y firebase-tools@latest deploy --only firestore:rules
```

The rules normalize legacy Calhoun badge numbers (200–225) stored as `departmentId` so administrators assigned to department `5` can write roster records.

## Verification plan

1. Sign in with a Firebase user that has a `members/{uid}` document.
2. Complete and submit an inspection while offline.
3. Reconnect and tap **Sync now** on the dashboard.
4. Confirm the inspection document appears under `departments/{departmentId}/inspections/{inspectionId}`.
5. Attach a photo to a failed item, submit, sync, and confirm the Storage object and attachment metadata upload.

## Emulator testing (recommended)

```bash
npx -y firebase-tools@latest emulators:start --only auth,firestore,storage
```

Point the Android app at emulators during development if desired (requires additional Android emulator host configuration).
