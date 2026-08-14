package com.medicore.common;

/** HTTP-mapped domain exception. Framework-free so the policy core can throw it. */
public class ApiException extends RuntimeException {
    private final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
