# Implement Persistent Membership and Authentication

Transition the authentication and membership layer from mocks to SQLDelight persistence. This fulfills Milestone 1 of the project and enables offline session recovery.

## User Review Required

> [!IMPORTANT]
> This change introduces a `SessionEntity` in the local database to track the logged-in user across app restarts. This is a foundational step for offline-first usability.

> [!NOTE]
> For this milestone, "Login" is simulated locally by checking if a member exists in the database with the provided email. Any password will be accepted if the email matches a seeded or registered member.

## Proposed Changes

### Database Layer

#### [MODIFY] [FirestationOps.sq](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/sqldelight/com/example/firestationops/db/FirestationOps.sq)
- Add `DepartmentEntity` table.
- Add `MemberEntity` table.
- Add `SessionEntity` table to store the current active user ID.
- Add CRUD queries for all new entities.

#### [MODIFY] [FirestationOpsDatabase.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestationops/db/FirestationOpsDatabase.kt)
- Add methods: `getAllDepartments()`, `getDepartmentById()`, `insertDepartment()`.
- Add methods: `getMemberByEmail()`, `getMemberById()`, `insertMember()`.
- Add methods: `getCurrentSession()`, `setSession()`, `clearSession()`.

---

### Domain Models

#### [MODIFY] [Role.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/model/Role.kt)
- Annotate `Role` enum with `@Serializable` to support JSON persistence in the database.

---

### Repository Layer

#### [NEW] [PersistentAuthRepository.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/repository/persistent/PersistentAuthRepository.kt)
- Implement `AuthRepository` using `FirestationOpsDatabase`.
- Handle session recovery in `init { ... }` by checking `SessionEntity`.
- Seed a default department and admin member if the database is empty.

#### [NEW] [PersistentDepartmentRepository.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestationops/domain/repository/persistent/PersistentDepartmentRepository.kt)
- Implement `DepartmentRepository` using `FirestationOpsDatabase`.

---

### Application Wiring

#### [MODIFY] [MainActivity.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/androidApp/src/main/kotlin/com/example/firestationops/MainActivity.kt)
- Inject `PersistentAuthRepository` instead of `MockAuthRepository`.

#### [MODIFY] [main.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/desktopApp/src/main/kotlin/com/example/firestationops/main.kt)
- Inject `PersistentAuthRepository` instead of `MockAuthRepository`.

## Verification Plan

### Automated Tests
- `PersistentAuthRepositoryTest`: Verify login, logout, and session recovery from DB.
- `PersistentDepartmentRepositoryTest`: Verify member and department lookups.

### Manual Verification
- **Login Flow**: Open the app, enter `admin@example.com`, and verify redirection to the dashboard.
- **Persistence**: Close and restart the app; verify it opens directly to the dashboard (session recovered).
- **Logout**: Tap Logout and verify the app returns to the login screen and session is cleared from DB.
