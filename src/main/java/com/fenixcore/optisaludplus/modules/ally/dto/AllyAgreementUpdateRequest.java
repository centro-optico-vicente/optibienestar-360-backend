package com.fenixcore.optisaludplus.modules.ally.dto;

import com.fenixcore.optisaludplus.modules.ally.entity.AllyAgreement.AgreementType;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

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
        Boolean active
) {}
