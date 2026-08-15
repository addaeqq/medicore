package com.medicore.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Failed-login bookkeeping for FR-AUTH-06.
 *
 * This exists as its own bean for one reason: a rejected login ends by throwing
 * ApiException, and Spring rolls a transaction back on any RuntimeException. Counting
 * the attempt inside that same transaction therefore threw the count away with it, so
 * the account never locked and every failed attempt vanished from the audit log.
 * REQUIRES_NEW commits the attempt in its own transaction before the caller throws.
 *
 * The increment is a single UPDATE so concurrent attempts cannot lose a count to a
 * read-modify-write race.
 */
@Component
public class LoginAttempts {

    private final JdbcTemplate jdbc;

    public LoginAttempts(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Counts one failure and locks the account once the threshold is reached. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId, int maxAttempts, int windowMinutes) {
        jdbc.update("""
            UPDATE users
               SET failed_logins = failed_logins + 1,
                   locked_until  = CASE WHEN failed_logins + 1 >= ?
                                        THEN now() + make_interval(mins => ?)
                                        ELSE locked_until END,
                   updated_at    = now()
             WHERE user_id = ?
            """, maxAttempts, windowMinutes, userId);

        // Written here too, so the attempt survives the caller's rollback (FR-EMR-06).
        jdbc.update("""
            INSERT INTO audit_log (user_id, action, meta)
            VALUES (?, 'auth.login_failed',
                    jsonb_build_object('attempts', (SELECT failed_logins FROM users WHERE user_id = ?),
                                       'locked',   (SELECT locked_until IS NOT NULL FROM users WHERE user_id = ?)))
            """, userId, userId, userId);
    }

    /** Clears the counter after a successful login. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(UUID userId) {
        jdbc.update("UPDATE users SET failed_logins = 0, locked_until = NULL, updated_at = now() WHERE user_id = ?",
            userId);
    }
}
