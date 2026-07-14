package com.fenixcore.optibienestar360.core.exception;

import java.time.Instant;

/**
 * 423 — account temporarily locked (anti-brute-force). Carries the timestamp
 * until which the lock is in effect; {@link GlobalExceptionHandler} exposes
 * it as the {@code lockedUntil} property of the problem+json response.
 */
public class AccountLockedException extends LocalizedBusinessException {

    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("auth.account.locked");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }
}
