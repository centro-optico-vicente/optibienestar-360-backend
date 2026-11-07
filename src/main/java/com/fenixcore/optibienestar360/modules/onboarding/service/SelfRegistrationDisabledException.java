package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.core.exception.ForbiddenDetailsException;

import java.util.Map;

/**
 * A public self-registration endpoint was called while that registration is
 * switched off in the onboarding policy (hub ADR 0037 §1). Rendered as a 403
 * whose {@code code} property is {@link #CODE}, so the form can send the
 * person to the login page.
 */
public class SelfRegistrationDisabledException extends ForbiddenDetailsException {

    public static final String CODE = "SELF_REGISTRATION_DISABLED";

    public SelfRegistrationDisabledException(String messageCode) {
        super(messageCode, Map.of("code", CODE));
    }
}
