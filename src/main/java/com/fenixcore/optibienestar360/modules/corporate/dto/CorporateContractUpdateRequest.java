package com.fenixcore.optibienestar360.modules.corporate.dto;

import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract.PayerMode;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/corporate-contracts/{uuid}}. PATCH-style:
 * every field is optional and {@code null} means "leave unchanged" (same
 * convention as {@code PlanUpdateRequest}). A supplied {@code planUuid} is
 * re-validated for the CORPORATIVO type.
 */
public record CorporateContractUpdateRequest(
        UUID planUuid,
        @Size(max = 200) String institutionName,
        @Size(max = 20) String institutionTaxId,
        UUID contactUserUuid,
        PayerMode payerMode,
        @PositiveOrZero Integer expectedMemberCount,
        Boolean active,
        String status
) {}
