# AGENTS.md — FirestationOps

## Project purpose

FirestationOps is an offline-first, cross-platform operations application for volunteer fire departments. It reduces paper workflows and improves accountability for:

- Apparatus and vehicle inspections.
- Deficiency and maintenance tracking.
- Incident and scene reports.
- Basic incident-command activities, resource assignments, and command timelines.
- Searchable records, exports, and audit trails.

The primary clients are Android phones/tablets used in stations and apparatus and a Windows desktop application used by officers and administrators. The project must remain practical for volunteer departments, including departments with unreliable cellular coverage.

## Product priorities

Apply these priorities in order whenever making a design or implementation decision:

1. Safety, reliability, and data integrity.
2. Offline usability in low/no-connectivity environments.
3. Fast, low-friction field workflows on Android.
4. Clear accountability: who did what, when, and to which record.
5. Security and department-level data isolation.
6. Maintainable Kotlin Multiplatform code with small, testable changes.
7. Cross-platform UI reuse where it does not compromise the Android field experience.

This application supports department operations. It does not replace dispatch, radio communications, CAD, SOPs, medical documentation systems, or incident commander judgment.

## Technology direction

- Language: Kotlin.
- UI: Compose Multiplatform.
- Targets: Android first; Windows desktop second through Compose Desktop.
- Architecture: Kotlin Multiplatform shared domain/data/presentation layers with platform-specific integrations.
- Backend, initial direction: Firebase Authentication, Cloud Firestore, Cloud Storage, Firebase Cloud Messaging, and Cloud Functions or Cloud Run as appropriate.
- Local data: offline-first local persistence. Do not make operational screens depend on an active network connection.
- Build system: Gradle Kotlin DSL with the Gradle wrapper committed to version control.
- Source control: Git and GitHub.

Do not add a dependency, framework, backend service, or build-tool upgrade without explaining why it is needed and checking compatibility with Kotlin Multiplatform, Android, and desktop targets.

## Operating rules for AI agents

### Work in small, reviewable increments

- Make one focused change per task or pull request.
- Do not generate an entire application, large unrelated refactor, or broad dependency upgrade in one change.
- Before changing code, inspect the relevant existing files and follow their patterns unless they violate this document.
- Preserve working behavior unless the task explicitly changes it.
- Prefer boring, explicit, maintainable code over clever abstractions.
- Do not fabricate APIs, Firebase behavior, Gradle configuration, library methods, compliance claims, or file paths. If unsure, identify the uncertainty and ask for documentation or propose a verification step.

### Do not commit directly to main

- Treat `main` as protected and releasable.
- Use a focused branch for every change.
- Suggested branch prefixes:
  - `feature/` for user-facing functionality.
  - `fix/` for defects.
  - `chore/` for maintenance, tooling, and dependency work.
  - `docs/` for documentation-only changes.
  - `test/` for test-only work.
- Keep commits small and descriptive.
- Do not modify or commit secrets, release keystores, production credentials, Firebase service-account keys, or private member/incident data.

### Verify before declaring work complete

- Run the smallest relevant test suite after a change.
- For shared code, test shared domain/validation logic.
- For Android UI/platform work, build and run the Android target when feasible.
- For desktop changes, build the desktop target when feasible.
- For changes that affect build configuration, run a clean Gradle build before recommending merge.
- Report exactly what was changed, what was tested, what was not tested, and any remaining risks.

## Repository layout

Use the existing repository structure if it differs. As the project grows, prefer a structure similar to this:

```text
FirestationOps/
├── composeApp/
│   ├── src/commonMain/         # Shared Compose UI, view models, domain-facing code
│   ├── src/commonTest/         # Shared tests
│   ├── src/androidMain/        # Android-only integrations
│   ├── src/androidUnitTest/    # Android unit tests
│   └── src/desktopMain/        # Windows/desktop-only integrations
├── shared/
│   ├── domain/                 # Pure models, use cases, validation, business rules
│   ├── data/                   # Repository implementations, local/remote sources, sync
│   └── core/                   # Shared utilities and cross-cutting abstractions
├── backend/                    # Optional Cloud Run/Ktor or server-side code
├── firebase/
│   ├── firestore.rules
│   ├── storage.rules
│   ├── firestore.indexes.json
│   └── README.md
├── docs/
│   ├── architecture.md
│   ├── data-model.md
│   ├── permissions.md
│   ├── offline-sync.md
│   └── workflows.md
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── AGENTS.md
└── README.md
```

Do not force this structure prematurely. Create modules only when there is a clear cohesion, testability, or target-separation benefit.

## Architecture rules

### Dependency direction

Use a clean, testable dependency direction:

```text
UI / Presentation → Domain ← Data
Platform integrations → Data interfaces / platform abstractions
```

- `domain` must not import Compose, Firebase, Android SDK, desktop APIs, or database/network implementation details.
- UI must not call Firebase SDKs, Storage SDKs, or HTTP clients directly.
- UI communicates through view models/presenters and domain use cases/repositories.
- Data implementations map remote/local DTOs into domain models.
- Platform capabilities such as camera, QR scanning, GPS, notifications, secure storage, printing, and file picking must be represented by interfaces in shared code and implemented in platform source sets.

### Prefer explicit state

- Model loading, success, empty, validation-error, offline/pending-sync, and failure states explicitly.
- Never discard a user-entered inspection or report because a network call failed.
- Do not use exceptions as normal UI control flow.
- Surface actionable error messages to the user while logging technical detail safely.

### Dependency injection

- Keep dependency injection simple and explicit initially.
- Constructor-inject dependencies.
- Do not introduce a DI framework unless the project’s module count and test setup justify it.
- If a DI framework is introduced, document the decision and keep dependencies compatible with all intended targets.

## Offline-first requirements

Offline behavior is a core product requirement, not an enhancement.

- Reads and writes for routine field workflows must work without an active network connection.
- Save user work locally before attempting remote synchronization.
- Persist drafts automatically for inspections and incident reports.
- Track synchronization state explicitly, for example: `LOCAL_ONLY`, `PENDING_SYNC`, `SYNCED`, `SYNC_FAILED`, `CONFLICT`.
- Show a clear sync state in operational workflows; never imply a record has reached the server if it has not.
- Queue writes and attachment uploads for retry when connectivity returns.
- Ensure retries are idempotent: retrying the same operation must not create duplicate inspections, reports, deficiencies, or command-log entries.
- Preserve both versions when a conflict cannot be safely resolved automatically; require an authorized user to resolve it.
- Record a server timestamp after synchronization but retain the field-entry time captured on the device.
- Do not silently overwrite finalized reports or another user’s meaningful changes.

## Domain terminology

Use consistent terms. Do not replace these with vague alternatives in models, APIs, or user-facing text without a deliberate product decision.

| Term | Meaning |
|---|---|
| Department | A tenant/organization using the product |
| Station | A physical station or organizational station within a department |
| Apparatus | A department vehicle or operational unit, such as an engine, tanker, brush truck, rescue, or command vehicle |
| Inspection template | Versioned checklist definition for an apparatus type or workflow |
| Inspection | A completed or in-progress instance of an inspection template |
| Inspection item | A single checklist question/requirement within an inspection |
| Deficiency | A failed, missing, damaged, expired, or otherwise actionable inspection finding |
| Incident | A response, call, event, training incident, or scene record |
| Unit assignment | Assignment/status history for an apparatus or unit at an incident |
| Personnel assignment | Assignment/status history for a member at an incident |
| Command log entry | Chronological, attributable entry in an incident command timeline |
| Attachment | Photo, PDF, document, or other file associated with an operational record |
| Finalized | Reviewed/locked official record; changes require a documented correction or amendment |

Use `Apparatus` rather than `Vehicle` in domain names unless a feature genuinely covers non-apparatus vehicles.

## Core domain models

Keep models stable, explicit, serializable where needed, and independent from Firebase SDK model types.

Expected foundational concepts include:

```kotlin
Department
Station
Member
Apparatus
InspectionTemplate
InspectionTemplateItem
Inspection
InspectionResponse
Deficiency
Incident
IncidentUnitAssignment
PersonnelAssignment
CommandLogEntry
Attachment
AuditEvent
```

Every department-scoped operational record should carry `departmentId`. Where useful, include `stationId`, `apparatusId`, `incidentId`, template/version identifiers, record status, and audit fields.

Avoid storing dynamic, untyped maps as the primary domain representation. Map them at the persistence boundary into typed Kotlin models.

## Audit and record-integrity rules

Operational records need accountability.

- Record `createdAt`, `createdByUserId`, `updatedAt`, `updatedByUserId`, and a revision/version value where applicable.
- Preserve the original record author and time; do not replace them during edits.
- Finalization must be an explicit action with a user and timestamp.
- Do not hard-delete finalized inspections, incident reports, command-log entries, or audit events through normal user workflows.
- Prefer status changes, voiding with a reason, or an amendment/correction record.
- Record meaningful state transitions in audit events, including submitted, finalized, reopened, voided, out-of-service, returned-to-service, and corrected.
- Audit events should be append-only from the client’s perspective.
- Do not claim records are legally compliant, NFIRS/NERIS compliant, HIPAA compliant, or official replacements for required reports without a separately verified implementation and review.

## Inspection workflow rules

The first finished workflow should be apparatus inspections.

- Templates must be versioned. A completed inspection retains the template version and item text used at completion.
- An inspection response must support at least `PASS`, `FAIL`, and `NOT_APPLICABLE`.
- A failed item should require a note when the template marks the note as required.
- An out-of-service deficiency must require an explanatory note and at least one attachment/photo unless an authorized policy explicitly permits an exception.
- Do not erase prior completed inspection data when templates change.
- Support drafts, submission, finalization/review when appropriate, and printable/exportable summaries.
- Deficiencies must have severity and lifecycle state, such as `INFORMATIONAL`, `REPAIR_NEEDED`, `OUT_OF_SERVICE` and `OPEN`, `ASSIGNED`, `RESOLVED`, `VOIDED`.
- Returning apparatus to service requires an attributable action, timestamp, and resolution note.

## Incident and command workflow rules

Build these only after inspections/deficiencies are dependable.

- Incident records must support drafts and offline creation.
- Keep a chronological command log with source user, device/entry time, synchronization time, and optional incident timestamp.
- Treat command-log history as append-only. Corrections create a clearly linked correction/amendment entry rather than silently rewriting history.
- Track unit/personnel assignment status transitions explicitly.
- Make accountability/command workflows clear and fast, but do not represent the application as a substitute for radio traffic, dispatch, or incident commander judgment.
- Do not implement patient-care documentation or store protected health information in the MVP without a separate privacy, security, and compliance review.

## Firebase rules

Firebase is an implementation detail behind repositories, not the domain model.

- Use Firebase Authentication for authenticated users.
- Scope all department data by `departmentId`.
- Firestore Security Rules must verify authentication and department membership on every production read/write.
- Never deploy public/test-mode database or storage rules to a production Firebase project.
- Store attachment binary data in Cloud Storage; keep attachment metadata and ownership references in Firestore.
- Validate high-risk workflow rules both client-side for user feedback and server-side/security-side for integrity.
- Use server-generated timestamps for authoritative sync/audit timing where appropriate, while retaining client-captured field time.
- Use Firebase emulators for rule tests and integration testing whenever feasible.
- Keep `firestore.rules`, `storage.rules`, indexes, and deployment configuration under version control.
- Never commit service-account JSON, Firebase Admin SDK credentials, keystores, API secrets, or `.env` files containing real credentials.

## Security requirements

- Require authenticated access for all department data.
- Enforce role-based permissions. Initial roles may include `MEMBER`, `APPARATUS_OFFICER`, `OFFICER`, and `ADMIN`.
- Enforce department isolation at every data boundary. A user must never access another department’s records.
- Apply least privilege. Administrative actions require explicit roles.
- Keep sensitive information out of logs, crash reports, screenshots, and test fixtures.
- Do not include real incident narratives, addresses, member names, phone numbers, credentials, medical information, or photos in the repository.
- Use fake/example department data for development and tests.
- Use secure platform storage for authentication/session secrets where required.
- Document any data-retention, deletion, export, backup, or access-control behavior that affects operational records.

## Android platform rules

Android is the primary field platform.

- Design for phones and tablets, including lower-cost tablets mounted in apparatus.
- Support large touch targets and readable contrast; do not depend on hover-only UI or keyboard shortcuts.
- Minimize typing for field workflows.
- Ask for camera, location, notification, and storage/media permissions only when the user initiates a feature that needs them.
- Handle permission denial gracefully and provide a clear fallback or explanation.
- Keep camera, QR/barcode scanning, GPS, background work, notifications, and Android-specific Firebase SDK calls inside `androidMain` or Android-specific modules.
- Test critical field workflows on a physical Android device, not only an emulator.
- Do not assume connectivity, precise GPS, or unrestricted background execution.

## Desktop platform rules

The desktop target is primarily for Windows station/administrative use.

- Optimize for wide-screen dashboards, review queues, reports, search, printing, and exports.
- Do not make the desktop app the only place a field-critical action can be completed.
- Isolate desktop printing, native file dialogs, and platform-specific file handling from common code.
- Treat shared station PCs as potentially shared devices: support sign-out, avoid exposing cached sensitive information after logout, and do not persist secrets insecurely.

## UI and accessibility rules

- Use Compose components consistently and keep reusable components small.
- Follow Material 3 patterns unless a field-use requirement justifies deviation.
- Prefer clear labels such as `Submit inspection`, `Save draft`, `Mark out of service`, and `Return to service`; avoid ambiguous icons as the sole control.
- Use color as a supplement, not the only indicator of status or severity.
- Provide text status for pass/fail, sync state, severity, overdue state, and out-of-service state.
- Confirm destructive or operationally consequential actions.
- Never make users re-enter a long form because of an app crash, navigation event, or failed sync.
- Preserve draft state during configuration changes and process recreation where platform behavior requires it.

## Testing expectations

### Required tests for domain logic

Write unit tests for:

- Inspection validation and required-field rules.
- Severity/out-of-service rules.
- Deficiency lifecycle transitions.
- Role and permission decisions.
- Template version preservation.
- Audit-event generation for meaningful state changes.
- Sync queue retry/idempotency behavior.
- Conflict-detection and non-destructive resolution behavior.
- Finalization and correction/amendment rules.

### Test naming

Use descriptive test names that state behavior, such as:

```kotlin
fun outOfServiceDeficiency_requiresNoteAndAttachment()
fun inspection_keepsOriginalTemplateVersionAfterTemplateChanges()
fun retryingQueuedInspectionUpload_doesNotCreateDuplicateInspection()
fun memberCannotReadAnotherDepartmentsApparatus()
```

### Test data

- Use fictional departments, apparatus, personnel, locations, photos, and reports.
- Do not use production exports or real emergency-service records in test fixtures.
- Make dates/times deterministic in tests through injected clocks where practical.

## Code style

- Follow standard Kotlin style and existing repository formatting.
- Use meaningful, domain-specific names.
- Prefer immutable `val` properties and immutable state updates.
- Keep functions small and single-purpose.
- Avoid `!!`; handle nullable values deliberately.
- Avoid broad `catch (Exception)` blocks unless rethrowing/mapping with context and preserving cancellation semantics.
- Do not mix UI state, Firebase DTOs, and domain objects in one model.
- Use sealed interfaces/classes or explicit result types for state where they improve correctness.
- Add KDoc only for public APIs, non-obvious rules, or safety-critical behavior—not as filler.

## Dependency policy

Before adding a dependency:

1. Check that it supports the required KMP/Android/desktop targets.
2. Check licensing and maintenance status.
3. Prefer a stable, established dependency over an experimental one for core data/security workflows.
4. Add the version through the project’s central version-management approach.
5. Explain why existing Kotlin, Compose, Firebase, or standard-library functionality is insufficient.
6. Run the relevant build and tests after adding it.

Avoid adding libraries solely because an AI assistant suggests them.

## Gradle and toolchain policy

- Use Android Studio Stable as the normal development environment.
- Canary may be used only for deliberate, isolated experimentation.
- Keep Gradle, Android Gradle Plugin, Kotlin, Compose, Compose Multiplatform, Firebase BOM, and JDK versions explicit and committed.
- Upgrade one major toolchain component at a time in a dedicated branch.
- Do not accept automatic IDE build-file changes without reviewing the diff.
- Use the committed Gradle wrapper for local and CI builds.
- Keep `local.properties`, keystores, service-account files, and local environment files out of Git.

## Documentation expectations

Update documentation when a change affects:

- Architecture/module boundaries.
- Firestore data paths or security model.
- Permission roles.
- Offline sync or conflict behavior.
- Inspection templates/statuses/validation rules.
- Incident/command workflows.
- Data retention, exports, finalization, corrections, or audit behavior.
- Local development setup or required environment variables.

Maintain these living documents as the project matures:

- `README.md`: project overview and local setup.
- `docs/architecture.md`: modules, dependency direction, and platform boundaries.
- `docs/data-model.md`: entities, relationships, IDs, statuses, Firestore mapping.
- `docs/permissions.md`: role matrix and authorization rules.
- `docs/offline-sync.md`: local persistence, queue, retries, conflicts, status indicators.
- `docs/workflows.md`: inspection, deficiency, incident, and command workflows.

## Definition of done

A task is not complete until all applicable items are true:

- The requested behavior is implemented with a focused change.
- Business rules are implemented outside composables and are tested.
- The UI handles loading, empty, error, offline, and pending-sync states as applicable.
- No real credentials, personal data, or incident data were added to the repository.
- Relevant unit tests pass.
- Relevant Android/desktop build tasks pass, or any unrun checks are stated clearly.
- Firebase/permission/security-rule changes have tests or a documented verification plan.
- The code is formatted and follows existing conventions.
- Documentation is updated when architecture, data, permissions, or workflows changed.
- The final summary lists files changed, tests run, and known limitations.

## Suggested first milestones

Focus on delivering usable value in this order:

1. Project shell, authentication, department membership, and role model.
2. Apparatus/station setup and configurable inspection templates.
3. Offline-capable daily apparatus inspection with draft autosave.
4. Deficiency creation, severity, out-of-service handling, assignment, and resolution.
5. Photo attachment, sync status, and reliable retry behavior.
6. Officer dashboard for overdue inspections and open deficiencies.
7. PDF/CSV inspection export and printable desktop view.
8. Basic incident report with offline drafts and an append-only timeline.
9. Unit/personnel assignments and a lightweight incident-command board.

Do not begin advanced CAD integration, real-time location tracking, radio replacement, indoor firefighter tracking, SCBA telemetry, patient-care documentation, or complex ICS automation until the daily inspection and deficiency workflow is reliable, tested, and usable by real department members.

## AI task template

When proposing or implementing a task, use this format:

```text
Goal:
Scope:
Non-goals:
Files likely affected:
Acceptance criteria:
Offline/sync considerations:
Security/permission considerations:
Tests to add or run:
Manual verification:
Risks or open questions:
```

When reporting completion, use this format:

```text
Implemented:
Files changed:
Tests run:
Manual verification performed:
Not tested:
Known limitations / follow-up:
```
