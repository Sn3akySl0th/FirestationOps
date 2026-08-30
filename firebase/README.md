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

## Member provisioning

Each signed-in user must have a profile document:

```
members/{firebaseUid}
  departmentId: "your-department-id"
  email: "member@example.com"
  firstName: "Alex"
  lastName: "Rivera"
  roles: ["OFFICER"]
  isActive: true
```

On first Firebase sign-in, if a matching local seeded member exists by email, the app creates this document automatically using the Firebase UID.

## Deploy security rules

From the repository root:

```bash
npx -y firebase-tools@latest deploy --only firestore:rules,storage
```

Review `firebase/firestore.rules` and `firebase/storage.rules` before deploying to production.

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
