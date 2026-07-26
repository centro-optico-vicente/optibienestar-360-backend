package com.fenixcore.optibienestar360.modules.card.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Affiliate digital card ({@code GET /v1/me/digital-card}). Flat read model
 * drawn from {@code digital_cards_view} plus a freshly-generated QR image
 * (inline PNG {@code data:} URI encoding the member UUID) that an ally can scan
 * to pull the holder up in the validator.
 *
 * <p>{@code planName} / {@code membershipStatus} / {@code nextDueDate} are
 * {@code null} when the member has no active membership (enrolled but not yet
 * subscribed to a plan).</p>
 */
public record DigitalCardDto(
        UUID memberUuid,
        String fullName,
        String documentType,
        String documentNumber,
        String planName,
        String membershipStatus,
        LocalDate nextDueDate,
        LocalDate memberSince,
        String qrCodeDataUri
) {}
