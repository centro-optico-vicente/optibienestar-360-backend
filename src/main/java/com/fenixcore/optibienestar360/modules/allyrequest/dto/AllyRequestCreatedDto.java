package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;

import java.util.UUID;

/** Answer of the public form: the ally is PENDING and its login already works with the chosen password. */
public record AllyRequestCreatedDto(
        UUID allyUuid,
        Ally.RegistrationStatus registrationStatus,
        String email,
        String promoterName
) {}
