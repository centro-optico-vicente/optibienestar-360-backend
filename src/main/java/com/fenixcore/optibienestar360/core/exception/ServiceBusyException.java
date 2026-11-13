package com.fenixcore.optibienestar360.core.exception;

/**
 * A bounded server resource (e.g. PDF rendering) is saturated right now. Mapped to
 * 503 Service Unavailable with a {@code Retry-After} header so the client can retry shortly.
 * The message is a message-bundle code.
 */
public class ServiceBusyException extends RuntimeException {

    private final int retryAfterSeconds;

    public ServiceBusyException(String messageCode, int retryAfterSeconds) {
        super(messageCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
