package com.medicore.common;

import java.io.Serializable;
import java.util.UUID;

/** Authenticated principal stored in the server-side session (DD-02). Framework-free. */
public record SessionUser(UUID userId, String role, UUID staffId, UUID patientId) implements Serializable {}
