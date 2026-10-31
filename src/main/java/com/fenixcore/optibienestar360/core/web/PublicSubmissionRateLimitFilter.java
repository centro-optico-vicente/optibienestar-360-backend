package com.fenixcore.optibienestar360.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP throttle for the anonymous write endpoints — today the public
 * self-affiliation form ({@code POST /v1/public/affiliation-requests}), which
 * creates rows and, with a proof attached, uploads a file to storage without
 * any login. Sliding one-hour window kept in memory: per replica, reset on
 * restart, which is enough to stop a script from flooding the review queue
 * and the bucket. The client IP is the container-resolved one
 * ({@code server.forward-headers-strategy=NATIVE}), as in
 * {@link ClientRequestMetadata}.
 */
@Slf4j
@Component
public class PublicSubmissionRateLimitFilter extends OncePerRequestFilter {

    private static final String AFFILIATION_PATH = "/v1/public/affiliation-requests";
    private static final long WINDOW_MILLIS = Duration.ofHours(1).toMillis();
    /** Ceiling for the JSON variant of the form (multipart has its own servlet limits). */
    private static final long MAX_JSON_BYTES = 64 * 1024;
    /** Above this many tracked IPs, stale windows are swept before adding more. */
    private static final int SWEEP_THRESHOLD = 10_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final MessageSource messageSource;
    private final int maxPerHour;

    public PublicSubmissionRateLimitFilter(MessageSource messageSource,
                                           @Value("${app.public-rate-limit.affiliation-requests-per-hour:10}") int maxPerHour) {
        this.messageSource = messageSource;
        this.maxPerHour = maxPerHour;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && AFFILIATION_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // The JSON form is a few hundred bytes; multipart bodies are already capped
        // by spring.servlet.multipart.*. Without this, an anonymous client could make
        // the server parse megabytes of JSON on every request.
        String contentType = request.getContentType();
        boolean json = contentType != null && contentType.toLowerCase().startsWith(MediaType.APPLICATION_JSON_VALUE);
        if (json && request.getContentLengthLong() > MAX_JSON_BYTES) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            return;
        }
        String ip = ClientRequestMetadata.from(request).ipAddress();
        if (ip != null && !tryAcquire(ip, System.currentTimeMillis())) {
            log.warn("Public affiliation submissions throttled for {}", ip);
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Records the hit if the IP is under its hourly budget; false when over it. */
    boolean tryAcquire(String ip, long now) {
        if (hits.size() > SWEEP_THRESHOLD) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Long last = e.getValue().peekLast();
                    return last == null || now - last >= WINDOW_MILLIS;
                }
            });
        }
        Deque<Long> window = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= maxPerHour) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        String detail = messageSource.getMessage("public.rate_limit.exceeded", null,
                "Too many requests", LocaleContextHolder.getLocale());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(Duration.ofHours(1).toSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\""
                + detail.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }
}
