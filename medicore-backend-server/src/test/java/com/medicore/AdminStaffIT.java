package com.medicore;

import com.medicore.admin.AdminService;
import com.medicore.auth.AuthService;
import com.medicore.common.ApiException;
import com.medicore.repo.Repositories.DepartmentRepository;
import com.medicore.repo.Repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Staff administration against the real database (run: MEDICORE_IT=true gradle test).
 * Covers FR-ADM-01 and the two attachments that make an account usable at all.
 */
@SpringBootTest(properties = "medicore.seed=false")
@EnabledIfEnvironmentVariable(named = "MEDICORE_IT", matches = "true")
class AdminStaffIT {

    @Autowired AdminService admin;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired JdbcTemplate jdbc;

    /**
     * A real user row: audit_log.user_id is a foreign key, so a made-up id makes every
     * audited write fail. That is how the swallowed-audit defect was found.
     */
    private UUID actor;

    @org.junit.jupiter.api.BeforeEach
    void createActor() {
        actor = UUID.randomUUID();
        jdbc.update("INSERT INTO users (user_id, email, password_hash, role) VALUES (?,?,?,?)",
            actor, "adminit-actor-" + actor + "@t.test", "x", "sys_admin");
    }

    private String freshEmail() { return "adminit-" + UUID.randomUUID() + "@t.test"; }

    private UUID aDepartment() {
        return departments.findByName("AdminITDept")
            .orElseGet(() -> departments.save(new com.medicore.domain.Department(
                "AdminITDept", "clinical", new BigDecimal("50")))).getDepartmentId();
    }

    private UUID aWard() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT ward_id FROM wards LIMIT 1");
        if (!rows.isEmpty()) return (UUID) rows.get(0).get("ward_id");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO wards (ward_id, name, daily_tariff) VALUES (?,?,?)", id, "AdminIT Ward", 10);
        return id;
    }

    @Test
    void createsAStaffAccountThatCanImmediatelySignIn() {
        String email = freshEmail();
        UUID staffId = admin.createStaff(email, "Password123!", "Dr. Admin Test", "doctor",
            aDepartment(), null, actor);

        assertNotNull(staffId);
        var user = auth.login(email, "Password123!");         // the real proof: it works as an account
        assertEquals("doctor", user.role());
        assertEquals(staffId, user.staffId());
        assertNull(user.patientId());
    }

    /** FR-AUTH-02: the administrator's typed password must not survive as plaintext. */
    @Test
    void passwordIsHashedNotStored() {
        String email = freshEmail();
        admin.createStaff(email, "Password123!", "Pharm Test", "pharmacist", null, null, actor);

        String hash = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertNotEquals("Password123!", hash);
        assertTrue(hash.startsWith("$2"), "expected a bcrypt hash, got: " + hash);
    }

    @Test
    void doctorWithoutADepartmentIsRejected() {
        ApiException e = assertThrows(ApiException.class, () ->
            admin.createStaff(freshEmail(), "Password123!", "No Dept", "doctor", null, null, actor));
        assertEquals(422, e.status());
        assertTrue(e.getMessage().contains("department"));
    }

    @Test
    void nurseWithoutAWardIsRejected() {
        ApiException e = assertThrows(ApiException.class, () ->
            admin.createStaff(freshEmail(), "Password123!", "No Ward", "nurse", aDepartment(), null, actor));
        assertEquals(422, e.status());
        assertTrue(e.getMessage().contains("ward"));
    }

    @Test
    void nurseKeepsTheWardThatScopesTheirAccess() {
        UUID ward = aWard();
        UUID staffId = admin.createStaff(freshEmail(), "Password123!", "Nurse Test", "nurse",
            aDepartment(), ward, actor);
        assertEquals(ward, jdbc.queryForObject(
            "SELECT assigned_ward_id FROM staff WHERE staff_id = ?", UUID.class, staffId));
    }

    /** A ward on a non-nurse would imply ward-scoped access the role does not have. */
    @Test
    void wardIsIgnoredForRolesThatDoNotUseIt() {
        UUID staffId = admin.createStaff(freshEmail(), "Password123!", "Clerk Test", "billing_clerk",
            null, aWard(), actor);
        assertNull(jdbc.queryForObject("SELECT assigned_ward_id FROM staff WHERE staff_id = ?",
            UUID.class, staffId));
    }

    @Test
    void duplicateEmailIsRefused() {
        String email = freshEmail();
        admin.createStaff(email, "Password123!", "First", "receptionist", null, null, actor);
        ApiException e = assertThrows(ApiException.class, () ->
            admin.createStaff(email, "Password123!", "Second", "receptionist", null, null, actor));
        assertEquals(409, e.status());
    }

    @Test
    void patientsCannotBeCreatedThroughStaffAdministration() {
        ApiException e = assertThrows(ApiException.class, () ->
            admin.createStaff(freshEmail(), "Password123!", "Not Staff", "patient", null, null, actor));
        assertEquals(422, e.status());
    }

    /** Leavers are deactivated, not deleted: audit_log pins the identity (NFR-SEC-04). */
    @Test
    void deactivationBlocksSignInAndReactivationRestoresIt() {
        String email = freshEmail();
        UUID staffId = admin.createStaff(email, "Password123!", "Leaver Test", "lab_tech", null, null, actor);
        assertNotNull(auth.login(email, "Password123!"));

        admin.setActive(staffId, false, actor);
        ApiException e = assertThrows(ApiException.class, () -> auth.login(email, "Password123!"));
        assertEquals(401, e.status());

        admin.setActive(staffId, true, actor);
        assertNotNull(auth.login(email, "Password123!"));
    }

    @Test
    void administratorCannotDeactivateTheirOwnAccount() {
        String email = freshEmail();
        UUID staffId = admin.createStaff(email, "Password123!", "Self Test", "sys_admin", null, null, actor);
        UUID ownUserId = users.findByEmail(email).orElseThrow().getUserId();

        ApiException e = assertThrows(ApiException.class, () -> admin.setActive(staffId, false, ownUserId));
        assertEquals(422, e.status());
        assertTrue(users.findByEmail(email).orElseThrow().isActive(), "account must remain usable");
    }

    /** FR-AUTH-06: five failures lock an account; an administrator can clear it. */
    @Test
    void unlockClearsALockout() {
        String email = freshEmail();
        UUID staffId = admin.createStaff(email, "Password123!", "Locked Test", "receptionist", null, null, actor);
        for (int i = 0; i < 5; i++)
            assertThrows(ApiException.class, () -> auth.login(email, "WrongPassword1!"));
        assertEquals(423, assertThrows(ApiException.class, () -> auth.login(email, "Password123!")).status());

        admin.unlock(staffId, actor);
        assertNotNull(auth.login(email, "Password123!"));
    }

    /**
     * Regression: AuditService used to join the caller's transaction, so an audit row the
     * database rejected aborted the whole transaction and silently discarded the business
     * write while the caller was told it had succeeded. The audit now commits separately,
     * so a doomed audit row can only cost itself.
     */
    @Test
    void aFailingAuditWriteCannotDiscardTheStaffRecord() {
        UUID actorThatDoesNotExist = UUID.randomUUID();   // violates audit_log.user_id's FK
        String email = freshEmail();

        UUID staffId = admin.createStaff(email, "Password123!", "Audit Test", "pharmacist",
            null, null, actorThatDoesNotExist);

        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM staff WHERE staff_id = ?", Integer.class, staffId));
        assertNotNull(auth.login(email, "Password123!"), "the account must still work");
    }

    @Test
    void theRosterShowsAccountState() {
        String email = freshEmail();
        UUID staffId = admin.createStaff(email, "Password123!", "Roster Test", "management", null, null, actor);
        Map<String, Object> row = admin.listStaff().stream()
            .filter(r -> staffId.equals(r.get("staff_id"))).findFirst().orElseThrow();
        assertEquals("Roster Test", row.get("full_name"));
        assertEquals("management", row.get("role"));
        assertEquals(Boolean.TRUE, row.get("is_active"));
        assertEquals(Boolean.FALSE, row.get("locked"));
    }
}
