package com.fenixcore.optibienestar360.core.exception;

import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

/**
 * A 403 that also carries extra ProblemDetail properties — the forbidden
 * counterpart of {@link UnprocessableDetailsException}, for when the client
 * must know <i>why</i> it was refused to react (e.g. {@code terms.pending}
 * plus the {@code termType} to accept).
 *
 * <p>{@link #getMessage()} is the message code; it is also exposed as the
 * {@code code} property so clients can branch on it without parsing the
 * localized {@code detail}.</p>
 */
public class ForbiddenDetailsException extends AccessDeniedException {

    private final transient Map<String, Object> properties;

    public ForbiddenDetailsException(String messageCode, Map<String, Object> properties) {
        super(messageCode);
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
