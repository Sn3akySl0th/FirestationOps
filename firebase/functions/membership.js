const { HttpsError } = require("firebase-functions/v2/https");

const ALLOWED_ROLES = new Set([
  "MEMBER",
  "APPARATUS_OFFICER",
  "OFFICER",
  "ADMIN",
]);

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function createMembershipService({ auth, firestore, logger, clock = () => Date.now() }) {
  async function provisionDepartmentMember(request) {
    const actorUid = requireAuthenticatedUid(request);
    const input = validateProvisionInput(request.data);
    let createdUid = null;
    let departmentId = null;

    try {
      const actor = await loadActiveAdmin(actorUid);
      departmentId = actor.departmentId;
      await ensureEmailAvailable(input.email);

      const userRecord = await auth.createUser({
        email: input.email,
        password: input.password,
        displayName: `${input.firstName} ${input.lastName}`,
        disabled: false,
      });
      createdUid = userRecord.uid;

      const now = clock();
      const member = memberDocument({
        uid: createdUid,
        departmentId,
        input,
        createdAt: now,
        updatedAt: now,
      });

      await firestore.runTransaction(async (transaction) => {
        await requireActiveAdminInTransaction(transaction, actorUid, departmentId);
        transaction.create(firestore.doc(`members/${createdUid}`), member);
        transaction.create(
          firestore.doc(`departments/${departmentId}/members/${createdUid}`),
          member,
        );
      });

      await synchronizeMembershipAuthority(member, null);
      logger.info("department_member_provisioned", {
        actorUid,
        targetUid: createdUid,
        departmentId,
      });
      return { member: sanitizeMember(member) };
    } catch (error) {
      if (createdUid) {
        await cleanupFailedProvision(createdUid, departmentId);
      }
      logFailure("department_member_provision_failed", error, {
        actorUid,
        targetUid: createdUid,
        departmentId,
      });
      throw mapCallableError(error);
    }
  }

  async function updateDepartmentMember(request) {
    const actorUid = requireAuthenticatedUid(request);
    const input = validateUpdateInput(request.data);
    let departmentId = null;
    let previousMember = null;
    let updatedMember = null;

    try {
      const actor = await loadActiveAdmin(actorUid);
      departmentId = actor.departmentId;
      const targetRef = firestore.doc(`members/${input.targetUserId}`);
      const targetSnapshot = await targetRef.get();
      if (!targetSnapshot.exists) {
        throw new HttpsError("not-found", "Member not found.");
      }

      previousMember = targetSnapshot.data();
      requireSameDepartment(previousMember, departmentId);
      const targetAuthUser = await auth.getUser(input.targetUserId);
      if (input.email !== String(targetAuthUser.email || "").toLowerCase()) {
        await ensureEmailAvailable(input.email, input.targetUserId);
      }

      const now = clock();
      await firestore.runTransaction(async (transaction) => {
        const [actorSnapshot, currentTargetSnapshot] = await Promise.all([
          transaction.get(firestore.doc(`members/${actorUid}`)),
          transaction.get(targetRef),
        ]);
        requireActiveAdminSnapshot(actorSnapshot, actorUid, departmentId);
        if (!currentTargetSnapshot.exists) {
          throw new HttpsError("not-found", "Member not found.");
        }

        const currentTarget = currentTargetSnapshot.data();
        requireSameDepartment(currentTarget, departmentId);
        if (isActiveAdmin(currentTarget) &&
            (!input.roles.includes("ADMIN") || input.isActive !== true)) {
          await requireAnotherActiveAdmin(transaction, departmentId, input.targetUserId);
        }

        updatedMember = memberDocument({
          uid: input.targetUserId,
          departmentId,
          input,
          createdAt: numberOrFallback(currentTarget.createdAt, now),
          updatedAt: now,
        });
        transaction.set(targetRef, updatedMember);
        transaction.set(
          firestore.doc(`departments/${departmentId}/members/${input.targetUserId}`),
          updatedMember,
        );
      });

      if (input.email !== String(targetAuthUser.email || "").toLowerCase() ||
          targetAuthUser.displayName !== `${input.firstName} ${input.lastName}`) {
        try {
          await auth.updateUser(input.targetUserId, {
            email: input.email,
            displayName: `${input.firstName} ${input.lastName}`,
          });
        } catch (error) {
          await restoreMemberPairIfUnchanged(previousMember, updatedMember);
          throw error;
        }
      }

      await synchronizeMembershipAuthority(updatedMember, previousMember);
      logger.info("department_member_updated", {
        actorUid,
        targetUid: input.targetUserId,
        departmentId,
        authorityChanged: membershipAuthorityChanged(previousMember, updatedMember),
      });
      return { member: sanitizeMember(updatedMember) };
    } catch (error) {
      logFailure("department_member_update_failed", error, {
        actorUid,
        targetUid: input.targetUserId,
        departmentId,
      });
      throw mapCallableError(error);
    }
  }

  async function deactivateDepartmentMember(request) {
    const actorUid = requireAuthenticatedUid(request);
    const input = validateDeactivateInput(request.data);
    let departmentId = null;
    let previousMember = null;
    let deactivatedMember = null;

    try {
      const actor = await loadActiveAdmin(actorUid);
      departmentId = actor.departmentId;
      const targetRef = firestore.doc(`members/${input.targetUserId}`);
      const now = clock();

      await firestore.runTransaction(async (transaction) => {
        const [actorSnapshot, targetSnapshot] = await Promise.all([
          transaction.get(firestore.doc(`members/${actorUid}`)),
          transaction.get(targetRef),
        ]);
        requireActiveAdminSnapshot(actorSnapshot, actorUid, departmentId);
        if (!targetSnapshot.exists) {
          throw new HttpsError("not-found", "Member not found.");
        }

        previousMember = targetSnapshot.data();
        requireSameDepartment(previousMember, departmentId);
        if (isActiveAdmin(previousMember)) {
          await requireAnotherActiveAdmin(transaction, departmentId, input.targetUserId);
        }

        deactivatedMember = {
          ...previousMember,
          id: input.targetUserId,
          departmentId,
          isActive: false,
          updatedAt: now,
        };
        transaction.set(targetRef, deactivatedMember);
        transaction.set(
          firestore.doc(`departments/${departmentId}/members/${input.targetUserId}`),
          deactivatedMember,
        );
      });

      await synchronizeMembershipAuthority(deactivatedMember, previousMember);
      logger.info("department_member_deactivated", {
        actorUid,
        targetUid: input.targetUserId,
        departmentId,
      });
      return { member: sanitizeMember(deactivatedMember) };
    } catch (error) {
      logFailure("department_member_deactivation_failed", error, {
        actorUid,
        targetUid: input.targetUserId,
        departmentId,
      });
      throw mapCallableError(error);
    }
  }

  async function loadActiveAdmin(uid) {
    const snapshot = await firestore.doc(`members/${uid}`).get();
    requireActiveAdminSnapshot(snapshot, uid);
    return snapshot.data();
  }

  async function requireActiveAdminInTransaction(transaction, uid, expectedDepartmentId) {
    const snapshot = await transaction.get(firestore.doc(`members/${uid}`));
    requireActiveAdminSnapshot(snapshot, uid, expectedDepartmentId);
    return snapshot.data();
  }

  async function requireAnotherActiveAdmin(transaction, departmentId, excludedUid) {
    const snapshot = await transaction.get(
      firestore.collection("members").where("departmentId", "==", departmentId),
    );
    const hasAnotherAdmin = snapshot.docs.some((document) =>
      document.id !== excludedUid && isActiveAdmin(document.data()),
    );
    if (!hasAnotherAdmin) {
      throw new HttpsError(
        "failed-precondition",
        "The department must keep at least one active administrator.",
      );
    }
  }

  async function ensureEmailAvailable(email, allowedUid = null) {
    try {
      const existing = await auth.getUserByEmail(email);
      if (existing.uid !== allowedUid) {
        throw new HttpsError("already-exists", "A sign-in account already exists for this email.");
      }
    } catch (error) {
      if (error?.code === "auth/user-not-found") {
        return;
      }
      throw error;
    }
  }

  async function synchronizeMembershipAuthority(member, previousMember) {
    try {
      await synchronizeClaims(member);
      if (previousMember && membershipAuthorityChanged(previousMember, member)) {
        await auth.revokeRefreshTokens(member.id);
      }
    } catch (error) {
      if (previousMember) {
        await restoreMemberPairIfUnchanged(previousMember, member);
      }
      throw new HttpsError(
        "aborted",
        "Membership was written to Firestore but claims synchronization failed. " +
          "The membership change was rolled back. Retry the operation or reconcile claims manually.",
      );
    }
  }

  async function synchronizeClaims(member) {
    const userRecord = await auth.getUser(member.id);
    const currentClaims = { ...(userRecord.customClaims || {}) };
    delete currentClaims.departmentId;
    delete currentClaims.roles;
    delete currentClaims.isActive;
    await auth.setCustomUserClaims(member.id, {
      ...currentClaims,
      departmentId: member.departmentId,
      roles: member.roles,
      isActive: member.isActive,
    });
  }

  async function cleanupFailedProvision(uid, departmentId) {
    try {
      const batch = firestore.batch();
      batch.delete(firestore.doc(`members/${uid}`));
      if (departmentId) {
        batch.delete(firestore.doc(`departments/${departmentId}/members/${uid}`));
      }
      await batch.commit();
    } catch (error) {
      logFailure("department_member_cleanup_failed", error, { targetUid: uid, departmentId });
    }
    try {
      await auth.deleteUser(uid);
    } catch (error) {
      if (error?.code !== "auth/user-not-found") {
        logFailure("department_member_auth_cleanup_failed", error, { targetUid: uid });
      }
    }
  }

  async function restoreMemberPairIfUnchanged(previousMember, attemptedMember) {
    if (!previousMember || !attemptedMember) return;
    await firestore.runTransaction(async (transaction) => {
      const canonicalRef = firestore.doc(`members/${attemptedMember.id}`);
      const current = await transaction.get(canonicalRef);
      if (!current.exists || current.data().updatedAt !== attemptedMember.updatedAt) {
        logger.warn("department_member_compensation_skipped", {
          targetUid: attemptedMember.id,
          departmentId: attemptedMember.departmentId,
        });
        return;
      }
      transaction.set(canonicalRef, previousMember);
      transaction.set(
        firestore.doc(
          `departments/${previousMember.departmentId}/members/${attemptedMember.id}`,
        ),
        previousMember,
      );
    });
  }

  function logFailure(event, error, context) {
    logger.error(event, {
      ...context,
      errorCode: String(error?.code || "internal"),
      errorType: String(error?.name || "Error"),
    });
  }

  return {
    provisionDepartmentMember,
    updateDepartmentMember,
    deactivateDepartmentMember,
    synchronizeClaims,
    synchronizeMembershipAuthority,
  };
}

function requireAuthenticatedUid(request) {
  const uid = request?.auth?.uid;
  if (typeof uid !== "string" || uid.length === 0) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  return uid;
}

function hasValidCanonicalRoles(roles) {
  return Array.isArray(roles) &&
    roles.length > 0 &&
    roles.every((role) => typeof role === "string" && ALLOWED_ROLES.has(role));
}

function requireActiveAdminSnapshot(snapshot, uid, expectedDepartmentId = null) {
  if (!snapshot.exists) {
    throw new HttpsError("permission-denied", "Active administrator membership required.");
  }
  const member = snapshot.data();
  if (member.id !== uid ||
      typeof member.departmentId !== "string" ||
      member.departmentId.length === 0 ||
      member.isActive !== true ||
      !hasValidCanonicalRoles(member.roles) ||
      !member.roles.includes("ADMIN") ||
      (expectedDepartmentId !== null && member.departmentId !== expectedDepartmentId)) {
    throw new HttpsError("permission-denied", "Active administrator membership required.");
  }
}

function requireSameDepartment(member, departmentId) {
  if (!member || member.departmentId !== departmentId) {
    throw new HttpsError("permission-denied", "Member is not in your department.");
  }
}

function validateProvisionInput(data) {
  assertPlainObject(data);
  assertAllowedKeys(data, [
    "email",
    "password",
    "firstName",
    "lastName",
    "memberNumber",
    "roles",
    "isActive",
  ]);
  const input = validateSharedMemberInput(data);
  const password = requiredString(data.password, "password", 6, 128, false);
  return { ...input, password };
}

function validateUpdateInput(data) {
  assertPlainObject(data);
  assertAllowedKeys(data, [
    "targetUserId",
    "email",
    "firstName",
    "lastName",
    "memberNumber",
    "roles",
    "isActive",
  ]);
  return {
    targetUserId: requiredString(data.targetUserId, "targetUserId", 1, 128),
    ...validateSharedMemberInput(data),
  };
}

function validateDeactivateInput(data) {
  assertPlainObject(data);
  assertAllowedKeys(data, ["targetUserId"]);
  return {
    targetUserId: requiredString(data.targetUserId, "targetUserId", 1, 128),
  };
}

function validateSharedMemberInput(data) {
  const email = requiredString(data.email, "email", 3, 254).toLowerCase();
  if (!EMAIL_PATTERN.test(email)) {
    throw new HttpsError("invalid-argument", "Enter a valid email address.");
  }
  if (!Array.isArray(data.roles) || data.roles.length === 0) {
    throw new HttpsError("invalid-argument", "Select at least one valid role.");
  }
  const roles = [...new Set(data.roles)];
  if (roles.some((role) => typeof role !== "string" || !ALLOWED_ROLES.has(role))) {
    throw new HttpsError("invalid-argument", "One or more roles are invalid.");
  }
  if (typeof data.isActive !== "boolean") {
    throw new HttpsError("invalid-argument", "isActive must be a boolean.");
  }
  const memberNumber = optionalString(data.memberNumber, "memberNumber", 32);
  return {
    email,
    firstName: requiredString(data.firstName, "firstName", 1, 80),
    lastName: requiredString(data.lastName, "lastName", 1, 80),
    memberNumber,
    roles,
    isActive: data.isActive,
  };
}

function assertPlainObject(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new HttpsError("invalid-argument", "A request object is required.");
  }
}

function assertAllowedKeys(data, allowedKeys) {
  const allowed = new Set(allowedKeys);
  const unknownKeys = Object.keys(data).filter((key) => !allowed.has(key));
  if (unknownKeys.length > 0) {
    throw new HttpsError("invalid-argument", "Request contains unsupported fields.");
  }
}

function requiredString(value, fieldName, minLength, maxLength, trim = true) {
  if (typeof value !== "string") {
    throw new HttpsError("invalid-argument", `${fieldName} must be a string.`);
  }
  const normalized = trim ? value.trim() : value;
  if (normalized.length < minLength || normalized.length > maxLength) {
    throw new HttpsError("invalid-argument", `${fieldName} has an invalid length.`);
  }
  return normalized;
}

function optionalString(value, fieldName, maxLength) {
  if (value === null || value === undefined || value === "") return null;
  return requiredString(value, fieldName, 1, maxLength);
}

function memberDocument({ uid, departmentId, input, createdAt, updatedAt }) {
  return {
    id: uid,
    departmentId,
    memberNumber: input.memberNumber,
    email: input.email,
    firstName: input.firstName,
    lastName: input.lastName,
    roles: input.roles,
    isActive: input.isActive,
    createdAt,
    updatedAt,
  };
}

function sanitizeMember(member) {
  return {
    id: member.id,
    departmentId: member.departmentId,
    memberNumber: member.memberNumber ?? null,
    email: member.email,
    firstName: member.firstName,
    lastName: member.lastName,
    roles: member.roles,
    isActive: member.isActive,
    createdAt: member.createdAt,
    updatedAt: member.updatedAt,
  };
}

function isActiveAdmin(member) {
  return member?.isActive === true &&
    hasValidCanonicalRoles(member.roles) &&
    member.roles.includes("ADMIN");
}

function membershipAuthorityChanged(previous, updated) {
  return previous.departmentId !== updated.departmentId ||
    previous.isActive !== updated.isActive ||
    JSON.stringify([...(previous.roles || [])].sort()) !==
      JSON.stringify([...(updated.roles || [])].sort());
}

function numberOrFallback(value, fallback) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function mapCallableError(error) {
  if (error instanceof HttpsError) return error;
  switch (error?.code) {
    case "auth/email-already-exists":
      return new HttpsError("already-exists", "A sign-in account already exists for this email.");
    case "auth/invalid-email":
      return new HttpsError("invalid-argument", "Enter a valid email address.");
    case "auth/invalid-password":
      return new HttpsError("invalid-argument", "The initial password is not valid.");
    case "auth/user-not-found":
      return new HttpsError("not-found", "Member sign-in account not found.");
    case 6:
    case "6":
    case "already-exists":
      return new HttpsError("already-exists", "A member with this identity already exists.");
    default:
      return new HttpsError("internal", "Unable to complete the member operation.");
  }
}

module.exports = {
  ALLOWED_ROLES,
  createMembershipService,
  hasValidCanonicalRoles,
  membershipAuthorityChanged,
};
