package com.fenixcore.optibienestar360.modules.corporate.dto;

import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract.PayerMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/corporate-contracts}.
 *
 * <p>{@code planUuid} must resolve to a {@code CORPORATIVO} plan — enforced by
 * the service ({@code corporate_contract.plan.not_corporate}), not a bean
 * validation. {@code contactUserUuid} is optional (the contract can be managed
 * centrally without a dedicated login).</p>
 */
public record CorporateContractCreateRequest(
        @NotNull UUID planUuid,
        @NotBlank @Size(max = 200) String institutionName,
        @NotBlank @Size(max = 20) String institutionTaxId,
        UUID contactUserUuid,
        @NotNull PayerMode payerMode,
        @PositiveOrZero Integer expectedMemberCount
) {}
