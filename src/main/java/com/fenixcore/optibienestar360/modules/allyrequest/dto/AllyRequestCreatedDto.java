package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;

import java.util.UUID;

/** Answer of the public form: the ally is PENDING and its login already works with the chosen password. */
public record AllyRequestCreatedDto(
        UUID allyUuid,
        Ally.RegistrationStatus registrationStatus,
        String email,
        String promoterName,
        /** Session of the login created in step 1 (same shape as {@code POST /v1/auth/login}); absent when none was opened. */
        LoginResponse session
) {

    public AllyRequestCreatedDto(UUID allyUuid, Ally.RegistrationStatus registrationStatus, String email,
                                 String promoterName) {
        this(allyUuid, registrationStatus, email, promoterName, null);
    }
}
