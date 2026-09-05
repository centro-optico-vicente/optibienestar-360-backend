package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/aliado/services} — the ally-side proposal
 * flow. Same fields as {@code AllyServiceCreateRequest} (admin-side) plus
 * {@code allyUuid} explicit in the body: a user can have memberships in
 * multiple allies (cadena de farmacias, etc.), so the request must say
 * which ally is being acted on.
 *
 * <p>The service layer validates that the JWT-authenticated user has an
 * active OWNER or STAFF membership on the target ally before persisting.
 * The new row lands at {@code reviewStatus=PROPOSED} (V11 column default);
 * admin moves it to APPROVED via the workflow endpoints, which logs the
 * actor + reason in {@code ally_service_review_log}.</p>
 */
public record ProposeAllyServiceRequest(
        @NotNull UUID allyUuid,
        @NotNull UUID serviceCategoryUuid,
        @NotBlank @Size(max = 200) String name,
        String description,

        @Digits(integer = 8, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal priceAmount,

        @Digits(integer = 3, fraction = 2)
        @DecimalMin(value = "0.0", inclusive = true)
        @DecimalMax(value = "100.0", inclusive = true)
        BigDecimal discountPct,

        Boolean requiresAppointment
) {}
