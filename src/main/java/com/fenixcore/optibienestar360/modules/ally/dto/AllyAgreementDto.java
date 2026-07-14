package com.fenixcore.optibienestar360.modules.ally.dto;

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
 */
public record AllyAgreementDto(
        UUID uuid,
        UUID allyUuid,
        AgreementType agreementType,
        LocalDate startDate,
        LocalDate endDate,
        String terms,
        String signedPdfUrl,
        String status,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
