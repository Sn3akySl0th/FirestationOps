# Permissions and department isolation

FirestationOps is an internal operational aid. It does not replace CAD, dispatch, radio communications, incident-command judgment, or official NFIRS/NERIS reporting.

## Authorization source

`members/{firebaseUid}` is the canonical membership record. Firebase clients may read only their own canonical record and may not create, update, or delete canonical membership. The nested `departments/{departmentId}/members/{uid}` record is a roster projection for same-department display and synchronization; it is not an authorization source.

Every department-scoped Firestore and Storage request requires an authenticated canonical member whose `isActive` value is `true` and whose `departmentId` exactly matches the department path. Client-supplied custom claims and nested roster records are not trusted for this decision.

## Initial role matrix

| Role | Initial permissions |
|---|---|
| `MEMBER` | Read department data, submit inspections, create deficiencies, create incident drafts, and append command-log entries. |
| `APPARATUS_OFFICER` | All `MEMBER` permissions plus apparatus status and deficiency operational actions. Catalog identity fields remain admin-only. |
| `OFFICER` | All `MEMBER` permissions plus manage incidents and assignments, activate or close incidents, and append linked command-log correction entries. Existing command-log entries remain immutable. |
| `ADMIN` | All operational permissions plus roster, role, catalog, and department administration. |

Roles do not bypass tenant isolation. An `ADMIN` in `dept-alpha` has no authority over `dept-bravo`.

## Server-controlled roster operations

Android administrators use callable Functions named `provisionDepartmentMember`, `updateDepartmentMember`, and `deactivateDepartmentMember`. Each Function:

- Authenticates the caller and reloads the caller's canonical membership.
- Requires an active `ADMIN` actor.
- Derives the authoritative department from that canonical record.
- Rejects client-supplied tenant fields and unknown roles.
- Writes canonical and nested roster records in one Firestore transaction.
- Prevents removal or deactivation of the final active administrator.
- Sets custom claims through the Admin SDK and revokes refresh tokens after authority changes.

Desktop cloud roster editing is intentionally unavailable until it can use the same callable flow. Desktop users receive an explicit explanation rather than a local-only success indication.

## Safe initial-admin bootstrap

The callable Functions require an existing active administrator, so the first administrator must be bootstrapped from a trusted server environment:

1. Select and verify the intended Firebase project; never run bootstrap work against an ambiguous default project.
2. Create the initial Firebase Authentication user through the Firebase console or Admin SDK. Do not place an initial password, service-account key, or exported credential in this repository.
3. From a trusted environment using Application Default Credentials, use the Admin SDK to write identical `members/{uid}` and `departments/{departmentId}/members/{uid}` records in one transaction. Set `id` to the Auth UID, the exact tenant `departmentId`, `roles: ["ADMIN"]`, and `isActive: true`.
4. Set the user's `departmentId`, `roles`, and `isActive` custom claims through the Admin SDK.
5. Verify both documents, verify that the department has at least one active administrator, and then have the administrator sign out and sign in again before using roster Functions.
6. Record the bootstrap as a controlled administrative action outside the client application. Remove any temporary elevated local access when complete.

Do not bootstrap by temporarily opening rules or by allowing the client to self-create membership.

## Required migration checklist

Complete and verify this checklist before deploying the tightened rules or callable roster workflow:

- [ ] **Canonical member normalization:** Every Firebase Auth user who should have access has exactly one `members/{uid}` document whose `id` equals the Auth UID and whose `departmentId` is the intended tenant. Move legacy badge-number values out of `departmentId` and into `memberNumber` where applicable.
- [ ] **Missing `isActive`:** Add an explicit Boolean `isActive` to every canonical record. Review the account before choosing `true`; do not automatically activate unknown records.
- [ ] **Malformed or unknown roles:** Replace missing, scalar, empty, misspelled, or unknown role values with a reviewed non-empty list containing only `MEMBER`, `APPARATUS_OFFICER`, `OFFICER`, or `ADMIN`.
- [ ] **Canonical versus nested roster reconciliation:** Make each nested `departments/{departmentId}/members/{uid}` projection exactly match its canonical record. Remove or archive stale pending/invite projections through a trusted migration process; never use them as authorization evidence.
- [ ] **Department-scoped document IDs:** Backfill or correct `departmentId` on department, station, apparatus, template, inspection, deficiency, attachment, incident, command-log, and assignment documents so the embedded value exactly matches the department path.
- [ ] **Active administrator coverage:** Confirm every department has at least one reviewed canonical member with `isActive: true` and `roles` containing `ADMIN` before rule deployment. Prefer two active administrators during migration to reduce lockout risk.
- [ ] **Claims and sessions:** After any role or active-state correction, update claims through trusted server code and revoke refresh tokens. Firestore and Storage authorization reads canonical membership immediately, but clients should still force-refresh their token or sign out and sign in again so application-visible claims are current.

Run the emulator suite after normalization and before production deployment. Retain a reviewed backup/export and a rollback procedure for the migration; do not place production member or incident data in test fixtures.

## Token freshness

Custom claims are server-managed convenience data, not the authoritative rules source. Claim changes appear on a newly issued ID token. Membership Functions revoke refresh tokens after role or active-state changes, but an already-issued ID token can remain present on a device until refresh. Canonical membership checks in Firestore and Storage rules make tenant changes and deactivation effective without waiting for claims to refresh. The affected user should still sign out and sign back in, or force an ID-token refresh, before relying on updated application-visible claims.

## Claims synchronization failures

Membership roster callables write canonical and nested Firestore records in a transaction before synchronizing custom claims and revoking refresh tokens. If claims synchronization or token revocation fails after the Firestore write:

- **Provision:** the callable rolls back the created Auth user and both membership documents.
- **Update or deactivation:** the callable rolls back the Firestore membership documents to the previous values and returns an `aborted` error with an explicit message. Retry the roster operation after resolving the Admin SDK failure, or reconcile claims manually through trusted server tooling and have affected users sign in again.
- **Manual reconciliation:** use the `syncMemberClaims` callable or Admin SDK to set `departmentId`, `roles`, and `isActive` from the canonical `members/{uid}` record, then revoke refresh tokens when authority changed.

Do not leave a successful roster UI state when claims synchronization failed; the callable response must remain unambiguous.
