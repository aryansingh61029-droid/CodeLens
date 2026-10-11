package com.example.plagiarism.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds concurrent CPU work and JSON bodies before deserialization. No shared submission state. */
@Component
public class RequestGuard extends OncePerRequestFilter {
    private final Semaphore slots;
    private final ObjectMapper mapper;
    public RequestGuard(ObjectMapper mapper, @Value("${app.max-concurrent-requests:2}") int concurrent) {
        if (concurrent < 1 || concurrent > 16) throw new IllegalArgumentException("Concurrency must be 1–16.");
        this.mapper = mapper; this.slots = new Semaphore(concurrent);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' https://cdn.tailwindcss.com; style-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        if (!request.getServletPath().startsWith("/api/")) { chain.doFilter(request, response); return; }
        response.setHeader("Cache-Control", "no-store");
        if (!slots.tryAcquire()) {
            response.setHeader("Retry-After", "2");
            error(response, HttpStatus.TOO_MANY_REQUESTS, "Server is busy; try again shortly."); return;
        }
        try {
            if (request.getContentType() != null && request.getContentType().startsWith("application/json")) {
                byte[] body = request.getInputStream().readNBytes(4 * 1024 * 1024 + 1);
                if (body.length > 4 * 1024 * 1024) {
                    error(response, HttpStatus.PAYLOAD_TOO_LARGE, "JSON body exceeds 4 MiB."); return;
                }
                HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
                    @Override public ServletInputStream getInputStream() {
                        ByteArrayInputStream input = new ByteArrayInputStream(body);
                        return new ServletInputStream() {
                            public int read() { return input.read(); }
                            public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                            public boolean isFinished() { return input.available() == 0; }
                            public boolean isReady() { return true; }
                            public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                        };
                    }
                };
                chain.doFilter(wrapped, response);
            } else chain.doFilter(request, response);
        } finally { slots.release(); }
    }

    private void error(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value()); response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
