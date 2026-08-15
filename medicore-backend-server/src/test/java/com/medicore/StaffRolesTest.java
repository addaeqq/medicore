package com.medicore;

import com.medicore.admin.StaffRoles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests for staff-role rules (FR-ADM-01, AC-03). */
class StaffRolesTest {

    @Test
    void patientsAndFamilyCannotBeCreatedAsStaff() {
        assertFalse(StaffRoles.isCreatable("patient"));
        assertFalse(StaffRoles.isCreatable("family"));
        assertTrue(StaffRoles.validate("patient", true, true).contains("register themselves"));
    }

    @Test
    void everyClinicalAndAdminRoleIsCreatable() {
        for (String role : new String[]{"doctor", "nurse", "pharmacist", "lab_tech",
                                        "receptionist", "billing_clerk", "management", "sys_admin"})
            assertTrue(StaffRoles.isCreatable(role), role + " should be creatable");
    }

    /** A doctor with no department cannot be booked at all, so the form must refuse it. */
    @Test
    void doctorRequiresADepartment() {
        assertNotNull(StaffRoles.validate("doctor", false, false));
        assertTrue(StaffRoles.validate("doctor", false, false).contains("department"));
        assertNull(StaffRoles.validate("doctor", true, false));
    }

    /** AC-03: ward scope resolves against assigned_ward_id, so a nurse without one sees nothing. */
    @Test
    void nurseRequiresAWard() {
        assertNotNull(StaffRoles.validate("nurse", true, false));
        assertTrue(StaffRoles.validate("nurse", true, false).contains("ward"));
        assertNull(StaffRoles.validate("nurse", true, true));
    }

    @Test
    void otherRolesNeedNeitherAttachment() {
        for (String role : new String[]{"pharmacist", "lab_tech", "billing_clerk", "management", "sys_admin"})
            assertNull(StaffRoles.validate(role, false, false), role + " should not require an attachment");
    }

    @Test
    void unknownAndEmptyRolesAreRejected() {
        assertNotNull(StaffRoles.validate("surgeon", true, true));
        assertNotNull(StaffRoles.validate("", true, true));
        assertNotNull(StaffRoles.validate(null, true, true));
    }

    @Test
    void staffTypeMirrorsTheAccountRole() {
        for (String role : StaffRoles.CREATABLE) assertEquals(role, StaffRoles.staffTypeFor(role));
    }
}
