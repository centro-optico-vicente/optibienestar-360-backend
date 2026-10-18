package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.UUID;

/** Output DTO for {@code /v1/admin/promoters/{promoterUuid}/references} (V180, dossier section IV). */
public record PromoterReferenceDto(
        UUID uuid,
        UUID promoterUuid,
        String firstName,
        String lastName,
        String documentType,
        String documentNumber,
        String phone,

        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "promoter_reference.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
