package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement.AgreementType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/allies/{allyUuid}/agreements} and
 * {@code GET /v1/admin/allies/{allyUuid}/agreements/{uuid}}.
 *
 * <p>{@code status} reflects the contract lifecycle (DRAFT / ACTIVE /
 * EXPIRED / TERMINATED) — stored in the shared {@code status} column of
 * {@link com.fenixcore.optibienestar360.core.entity.BaseEntity}.</p>
 *
 * <p>Scalars carry a localized {@code _Display} sibling (ADR 0014).</p>
 */
public record AllyAgreementDto(
        UUID uuid,
        UUID allyUuid,
        @Display(Display.Kind.ENUM) AgreementType agreementType,
        @Display(Display.Kind.DATE) LocalDate startDate,
        @Display(Display.Kind.DATE) LocalDate endDate,
        String terms,
        String signedPdfUrl,
        @Display(value = Display.Kind.ENUM, enumScope = "ally_agreement.status") String status,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
