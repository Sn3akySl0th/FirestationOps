# Milestone 1: Auth & Core Domain Models

This plan covers the initial setup of the core domain models and authentication logic for FirestationOps. This establishes the foundation for department-scoped data and user roles.

## User Review Required

> [!IMPORTANT]
> - **Module Choice**: I am proposing to place the domain models in `app:shared`'s `commonMain` for now to keep things simple, as per the "Do not force this structure prematurely" rule in `AGENTS.md`.
> - **Firebase Integration**: This plan defines the *interfaces* for Authentication. The actual Firebase implementation will follow in a separate task once the project shell is stable.
> - **Package Name**: I'll use `com.example.firestaionops.domain` (note: there is a typo in the project name "FirestaionOps" in the current file paths, I will stick to it for consistency with the existing code).

## Proposed Changes

### Core Models & Domain Logic

#### [NEW] [Department.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestaionops/domain/model/Department.kt)
Defines the `Department` entity, representing a fire department tenant.

#### [NEW] [Member.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestaionops/domain/model/Member.kt)
Defines the `Member` entity, representing a user within a department.

#### [NEW] [Role.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestaionops/domain/model/Role.kt)
Defines the roles: `MEMBER`, `APPARATUS_OFFICER`, `OFFICER`, `ADMIN`.

#### [NEW] [AuthRepository.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestaionops/domain/repository/AuthRepository.kt)
Interface for authentication operations (login, logout, current user state).

#### [NEW] [DepartmentRepository.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestaionops/domain/repository/DepartmentRepository.kt)
Interface for fetching department and member information.

---

### UI & Presentation

#### [MODIFY] [App.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestaionops/App.kt)
Introduce an `AuthGate` component that observes the authentication state and switches between a login screen and the main application content.

#### [NEW] [LoginScreen.kt](file:///C:/Users/clefe/AndroidStudioProjects/FirestaionOps/app/shared/src/commonMain/kotlin/com/example/firestaionops/ui/auth/LoginScreen.kt)
A simple login UI placeholder.

## Verification Plan

### Automated Tests
- Unit tests for domain model validation (e.g., ensuring a `Member` always has a `departmentId`).
- Mock tests for the `AuthGate` logic.

### Manual Verification
- Deploy to Android emulator to verify the "AuthGate" successfully toggles between Login and "Logged In" state using a mock `AuthRepository`.
