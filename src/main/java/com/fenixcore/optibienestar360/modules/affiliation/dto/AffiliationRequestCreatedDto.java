package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.util.UUID;

/**
 * Answer of {@code POST /v1/public/affiliation-requests} (201): the new
 * request, always {@code PENDING}, plus what the success screen shows — the
 * email the decision will be sent to and the advisor of the referral code.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AffiliationRequestCreatedDto(
        UUID uuid,
        @Display(value = Display.Kind.ENUM, enumScope = "affiliation_request.status") String status,
        String email,
        String advisorName
) {}
