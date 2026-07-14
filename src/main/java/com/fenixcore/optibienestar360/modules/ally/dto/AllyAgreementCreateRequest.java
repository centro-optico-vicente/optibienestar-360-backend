package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement.AgreementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for {@code POST /v1/admin/allies/{allyUuid}/agreements}.
 *
 * <p>The parent ally is resolved from the path; this record carries only the
 * agreement's own fields. Status defaults to {@code ACTIVE} on the DB side
 * when omitted (see V11 schema); pass it explicitly only if creating a
 * {@code DRAFT}.</p>
 */
public record AllyAgreementCreateRequest(
        @NotNull AgreementType agreementType,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        String terms,
        @Size(max = 500) String signedPdfUrl,
        String status
) {}
