package com.fenixcore.optibienestar360.core.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client IP + User-Agent captured from the current request, used as legal
 * evidence of an action (e.g. a T&C acceptance). {@code X-Forwarded-For} is
 * already resolved by the container ({@code server.forward-headers-strategy=NATIVE}),
 * so {@link HttpServletRequest#getRemoteAddr()} is the real client IP behind
 * the reverse proxy — same source {@code AuthService.resolveClientIp} uses.
 */
public record ClientRequestMetadata(String ipAddress, String userAgent) {

    /** Matches {@code terms_acceptances.ip_address VARCHAR(45)} (max IPv6 text length). */
    private static final int MAX_IP_LENGTH = 45;
    /** Matches {@code terms_acceptances.user_agent VARCHAR(400)}. */
    private static final int MAX_USER_AGENT_LENGTH = 400;

    public static final ClientRequestMetadata EMPTY = new ClientRequestMetadata(null, null);

    public static ClientRequestMetadata from(HttpServletRequest request) {
        if (request == null) {
            return EMPTY;
        }
        return new ClientRequestMetadata(
                truncate(stripBrackets(request.getRemoteAddr()), MAX_IP_LENGTH),
                truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH));
    }

    // Some containers wrap IPv6 addresses in brackets ("[::1]").
    private static String stripBrackets(String ip) {
        if (ip != null && ip.length() > 1 && ip.charAt(0) == '[' && ip.charAt(ip.length() - 1) == ']') {
            return ip.substring(1, ip.length() - 1);
        }
        return ip;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
