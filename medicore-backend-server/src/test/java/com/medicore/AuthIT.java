package com.medicore;

import com.medicore.auth.AuthService;
import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.repo.Repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authentication against the real database (run: MEDICORE_IT=true gradle test).
 *
 * Carries the evidence for FR-AUTH-01/02/06 that the retired Node.js Milestone 1
 * reference suite used to provide (Design change record v1.7): passwords are only
 * ever stored as bcrypt, and repeated failures lock the account for the configured
 * window rather than allowing unlimited guessing.
 */
@SpringBootTest(properties = "medicore.seed=false")
@EnabledIfEnvironmentVariable(named = "MEDICORE_IT", matches = "true")
class AuthIT {

    @Autowired AuthService auth;
    @Autowired UserRepository users;

    private String freshEmail() { return "authit-" + UUID.randomUUID() + "@t.test"; }

    /** FR-AUTH-02: never plaintext, never a fast digest — bcrypt at the configured cost. */
    @Test
    void passwordIsStoredAsBcryptAndNeverInClear() {
        String email = freshEmail();
        String raw = "Password123!";
        auth.registerPatient(email, raw, "Auth Test", LocalDate.of(1990, 1, 1), "other", null, null);

        String hash = users.findByEmail(email).orElseThrow().getPasswordHash();
        assertNotEquals(raw, hash, "password stored in clear");
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"),
            "not a bcrypt hash: " + hash);
        assertTrue(hash.contains("$12$"), "bcrypt cost is not 12: " + hash);
        assertTrue(hash.length() >= 59, "truncated bcrypt hash");
    }

    /** FR-AUTH-01: the same credentials round-trip through verification. */
    @Test
    void correctPasswordLogsIn() {
        String email = freshEmail();
        auth.registerPatient(email, "Password123!", "Auth Test", LocalDate.of(1990, 1, 1), "other", null, null);

        SessionUser user = auth.login(email, "Password123!");
        assertEquals("patient", user.role());
        assertNotNull(user.patientId());
        assertNull(user.staffId());
    }

    @Test
    void wrongPasswordIsRejectedWithoutRevealingWhichFieldFailed() {
        String email = freshEmail();
        auth.registerPatient(email, "Password123!", "Auth Test", LocalDate.of(1990, 1, 1), "other", null, null);

        ApiException wrongPassword = assertThrows(ApiException.class, () -> auth.login(email, "WrongPassword1!"));
        ApiException unknownUser = assertThrows(ApiException.class, () -> auth.login(freshEmail(), "Password123!"));
        assertEquals(401, wrongPassword.status());
        assertEquals(401, unknownUser.status());
        // NFR-SEC-02: an attacker must not learn whether the address exists.
        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
    }

    /**
     * FR-AUTH-06: five failures inside the window lock the account, and the lock holds
     * even when the correct password is then supplied — this is the check the Node
     * reference suite carried before it was retired.
     */
    @Test
    void repeatedFailuresLockTheAccountEvenAgainstTheCorrectPassword() {
        String email = freshEmail();
        auth.registerPatient(email, "Password123!", "Auth Test", LocalDate.of(1990, 1, 1), "other", null, null);

        for (int attempt = 1; attempt <= 5; attempt++) {
            ApiException e = assertThrows(ApiException.class, () -> auth.login(email, "WrongPassword1!"));
            assertEquals(401, e.status(), "attempt " + attempt + " should be a plain rejection");
        }

        ApiException locked = assertThrows(ApiException.class, () -> auth.login(email, "Password123!"));
        assertEquals(423, locked.status(), "account should be locked after 5 failures");
        assertNotNull(users.findByEmail(email).orElseThrow().getLockedUntil());
    }

    /** A successful login clears the counter so a user is not locked out by old noise. */
    @Test
    void successResetsTheFailureCounter() {
        String email = freshEmail();
        auth.registerPatient(email, "Password123!", "Auth Test", LocalDate.of(1990, 1, 1), "other", null, null);

        assertThrows(ApiException.class, () -> auth.login(email, "WrongPassword1!"));
        assertThrows(ApiException.class, () -> auth.login(email, "WrongPassword1!"));
        assertEquals(2, users.findByEmail(email).orElseThrow().getFailedLogins());

        auth.login(email, "Password123!");
        var after = users.findByEmail(email).orElseThrow();
        assertEquals(0, after.getFailedLogins());
        assertNull(after.getLockedUntil());
    }
}
