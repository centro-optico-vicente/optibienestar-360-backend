package com.fenixcore.optibienestar360.modules.card.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Affiliate digital card ({@code GET /v1/me/digital-card}). Flat read model
 * drawn from {@code digital_cards_view} plus a freshly-generated QR image.
 *
 * <p>{@code planName} / {@code membershipStatus} / {@code nextDueDate} are
 * {@code null} when the member has no active membership. Presentational
 * scalars carry a localized {@code _Display} sibling (hub ADR 0014).</p>
 */
public record DigitalCardDto(
        UUID memberUuid,
        String fullName,
        String documentType,
        String documentNumber,
        String planName,
        @Display(value = Display.Kind.ENUM, enumScope = "membership.status") String membershipStatus,
        @Display(Display.Kind.DATE) LocalDate nextDueDate,
        @Display(Display.Kind.DATE) LocalDate memberSince,
        String qrCodeDataUri
) {}
