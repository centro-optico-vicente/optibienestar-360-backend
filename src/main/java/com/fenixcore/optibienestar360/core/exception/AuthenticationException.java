package com.fenixcore.optibienestar360.core.exception;

/**
 * 401 — invalid credentials or expired/invalid auth token. The constructor
 * takes a message code (e.g. {@code auth.credentials.invalid}) that
 * {@link GlobalExceptionHandler} resolves against the request locale.
 */
public class AuthenticationException extends LocalizedBusinessException {

    public AuthenticationException(String messageCode, Object... args) {
        super(messageCode, args);
    }
}
