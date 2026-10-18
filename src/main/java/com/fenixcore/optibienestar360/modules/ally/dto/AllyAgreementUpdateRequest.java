package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement.AgreementType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/allies/{allyUuid}/agreements/{uuid}}.
 * PATCH semantics — null fields are ignored, present fields overwrite.
 */
public record AllyAgreementUpdateRequest(
        AgreementType agreementType,
        LocalDate startDate,
        LocalDate endDate,
        String terms,
        @Size(max = 500) String signedPdfUrl,
        String status,
        Boolean active,
        /** Must reference an {@code ALIADO} terms version (V179). */
        UUID termsVersionUuid,
        Boolean autoRenew,
        @Min(0) Integer noticeDays
) {}
