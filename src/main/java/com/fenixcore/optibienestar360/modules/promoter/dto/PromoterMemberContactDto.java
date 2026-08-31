package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterMemberContact.ContactType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A logged collection contact — an item of the history returned by
 * {@code GET /v1/promoter/me/contacts} and the echo of a just-created one.
 * {@code promised*} fields are present only for {@code PAYMENT_PROMISE}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromoterMemberContactDto(
        UUID uuid,
        UUID memberUuid,
        @Display(Display.Kind.ENUM) ContactType type,
        String note,
        @Display(Display.Kind.MONEY) BigDecimal promisedAmount,
        @Display(Display.Kind.DATE) LocalDate promisedAtDate,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {}
