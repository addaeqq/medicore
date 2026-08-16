package com.medicore.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

/** Central error mapping: no stack traces or internals leak to clients (NFR-SEC-02). */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        List<Map<String, String>> issues = e.getBindingResult().getFieldErrors().stream()
            .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
            .toList();
        return ResponseEntity.status(422).body(Map.of("error", "Validation failed", "details", issues));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(Map.of("error", "Conflict with existing data"));
    }

    /**
     * An unknown URL is a 404, not an internal error. Spring Boot 3.2 turned
     * throw-exception-if-no-handler-found on by default, so a mistyped path reaches the
     * catch-all below and reports "Internal error" — misleading, and it makes a client
     * mistake look like a server fault in the logs. Both not-found shapes are handled:
     * NoHandlerFoundException when no mapping matches, NoResourceFoundException when the
     * request falls through to the static resource handler.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(Exception e) {
        return ResponseEntity.status(404).body(Map.of("error", "Not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception e) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unhandled error", e);
        return ResponseEntity.status(500).body(Map.of("error", "Internal error"));
    }
}
