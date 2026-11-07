package com.fenixcore.optibienestar360.core.exception;

import java.util.Map;

/**
 * A 403 for a business rule (not a missing permission) that also carries
 * extra ProblemDetail properties — typically a machine-readable {@code code}
 * the client branches on. {@link #getMessageCode()} is localized like any
 * {@link LocalizedBusinessException}; every {@code properties} entry lands as
 * a top-level property of the problem body ({@link GlobalExceptionHandler}).
 */
public class ForbiddenDetailsException extends LocalizedBusinessException {

    private final transient Map<String, Object> properties;

    public ForbiddenDetailsException(String messageCode, Map<String, Object> properties) {
        super(messageCode);
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
