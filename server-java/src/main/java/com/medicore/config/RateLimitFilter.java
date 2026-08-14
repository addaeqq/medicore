package com.medicore.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * NFR-SEC-07: fixed-window rate limit on auth endpoints (50 req / 15 min / IP).
 * In-memory implementation adequate for a single instance; the Design §2.3 Bucket4j
 * swap is a drop-in at hardening — the filter boundary is identical.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT = 50;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    private record Window(long startMs, AtomicInteger count) {}
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/api/auth/")) {
            String ip = req.getRemoteAddr();
            long now = System.currentTimeMillis();
            Window w = windows.compute(ip, (k, v) ->
                (v == null || now - v.startMs() > WINDOW_MS) ? new Window(now, new AtomicInteger()) : v);
            if (w.count().incrementAndGet() > LIMIT) {
                res.setStatus(429);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Too many requests\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
