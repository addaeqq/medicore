package com.medicore.auth;

import com.medicore.common.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @NotBlank @Size(min = 2) String fullName,
        @NotNull LocalDate dob,
        @NotBlank @Pattern(regexp = "female|male|other") String sex,
        String phone, String address) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    // FR-PAT-01: public per policy matrix
    @PostMapping("/register")
    public ResponseEntity<AuthService.Registered> register(@Valid @RequestBody RegisterRequest r) {
        var out = auth.registerPatient(r.email(), r.password(), r.fullName(), r.dob(), r.sex(), r.phone(), r.address());
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    // FR-AUTH-01/06; session fixation defence: rotate the session id on login
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest r, HttpServletRequest req) {
        SessionUser su = auth.login(r.email(), r.password());
        var old = req.getSession(false);
        if (old != null) old.invalidate();
        req.getSession(true).setAttribute("user", su);
        return Map.of("user", su);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        var s = req.getSession(false);
        if (s != null) s.invalidate();
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        var s = req.getSession(false);
        Object user = s == null ? null : s.getAttribute("user");
        return java.util.Collections.singletonMap("user", user);
    }
}
