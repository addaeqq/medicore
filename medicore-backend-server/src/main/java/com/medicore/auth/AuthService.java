package com.medicore.auth;

import com.medicore.audit.AuditService;
import com.medicore.common.ApiException;
import com.medicore.common.SessionUser;
import com.medicore.domain.Patient;
import com.medicore.domain.UserAccount;
import com.medicore.repo.Repositories.PatientRepository;
import com.medicore.repo.Repositories.StaffRepository;
import com.medicore.repo.Repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** Registration + login with lockout (FR-PAT-01, FR-AUTH-01/02/06). */
@Service
public class AuthService {
    private final UserRepository users;
    private final PatientRepository patients;
    private final StaffRepository staff;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final LoginAttempts attempts;
    private final int maxAttempts;
    private final int windowMinutes;

    public AuthService(UserRepository users, PatientRepository patients, StaffRepository staff,
                       PasswordEncoder encoder, AuditService audit, LoginAttempts attempts,
                       @Value("${medicore.lockout.max-attempts:5}") int maxAttempts,
                       @Value("${medicore.lockout.window-minutes:15}") int windowMinutes) {
        this.users = users; this.patients = patients; this.staff = staff;
        this.encoder = encoder; this.audit = audit; this.attempts = attempts;
        this.maxAttempts = maxAttempts; this.windowMinutes = windowMinutes;
    }

    public record Registered(java.util.UUID userId, java.util.UUID patientId, String mrn) {}

    @Transactional
    public Registered registerPatient(String email, String rawPassword, String fullName,
                                      LocalDate dob, String sex, String phone, String address) {
        try {
            UserAccount user = new UserAccount(email.toLowerCase(), encoder.encode(rawPassword), "patient");
            users.saveAndFlush(user);
            String mrn = "MRN-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
            Patient patient = new Patient(user.getUserId(), mrn, fullName, dob, sex, phone, address);
            patients.saveAndFlush(patient);
            audit.log(user.getUserId(), patient.getPatientId(), "patient.register_self", null, null);
            return new Registered(user.getUserId(), patient.getPatientId(), mrn);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(409, "Email already registered");
        }
    }

    @Transactional
    public SessionUser login(String email, String rawPassword) {
        UserAccount user = users.findByEmail(email.toLowerCase()).orElse(null);
        if (user == null || !user.isActive()) throw new ApiException(401, "Invalid email or password");
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))
            throw new ApiException(423, "Account temporarily locked. Try again later."); // FR-AUTH-06

        if (!encoder.matches(rawPassword, user.getPasswordHash())) {
            // Counted in its own transaction: throwing below rolls this one back, which
            // silently discarded the count and left the account unlockable (FR-AUTH-06).
            attempts.recordFailure(user.getUserId(), maxAttempts, windowMinutes);
            throw new ApiException(401, "Invalid email or password");
        }

        attempts.recordSuccess(user.getUserId());
        var staffRow = staff.findByUserId(user.getUserId()).orElse(null);
        var patientRow = patients.findByUserId(user.getUserId()).orElse(null);
        audit.log(user.getUserId(), null, "auth.login", null, null);
        return new SessionUser(user.getUserId(), user.getRole(),
            staffRow == null ? null : staffRow.getStaffId(),
            patientRow == null ? null : patientRow.getPatientId());
    }
}
