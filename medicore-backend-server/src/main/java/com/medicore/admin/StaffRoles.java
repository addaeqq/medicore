package com.medicore.admin;

import java.util.List;
import java.util.Set;

/**
 * Which staff roles exist and what each one must be attached to (FR-ADM-01).
 * Pure and framework-free, like PolicyEngine and LabWorkflow: the service wraps it
 * with persistence.
 *
 * The two mandatory attachments are not bureaucracy — they are what makes the
 * account work at all:
 *  - a doctor with no department cannot be booked (SchedulingService rejects it 422)
 *  - a nurse with no ward resolves to no patients, because WARD scope is evaluated
 *    against staff.assigned_ward_id (AC-03)
 * Creating either without its attachment produces an account that silently does
 * nothing, which is worse than a rejected form.
 */
public final class StaffRoles {
    private StaffRoles() {}

    /** Roles an administrator may create here. Patients self-register; family access is granted. */
    public static final List<String> CREATABLE = List.of(
        "doctor", "nurse", "pharmacist", "lab_tech", "receptionist", "billing_clerk", "management", "sys_admin");

    private static final Set<String> NEEDS_DEPARTMENT = Set.of("doctor");
    private static final Set<String> NEEDS_WARD = Set.of("nurse");

    public static boolean isCreatable(String role) { return CREATABLE.contains(role); }

    /** staff.staff_type mirrors the account role; kept as one rule so they cannot drift. */
    public static String staffTypeFor(String role) { return role; }

    public static boolean requiresDepartment(String role) { return NEEDS_DEPARTMENT.contains(role); }

    public static boolean requiresWard(String role) { return NEEDS_WARD.contains(role); }

    /**
     * @return null when the combination is valid, otherwise the reason to show the
     *         administrator.
     */
    public static String validate(String role, boolean hasDepartment, boolean hasWard) {
        if (role == null || role.isBlank()) return "Choose a role";
        if (!isCreatable(role))
            return "'" + role + "' cannot be created here (patients register themselves; "
                 + "family access is granted by the patient)";
        if (requiresDepartment(role) && !hasDepartment)
            return "A doctor needs a department — appointments cannot be booked without one";
        if (requiresWard(role) && !hasWard)
            return "A nurse needs an assigned ward — ward-scoped records resolve against it";
        return null;
    }
}
