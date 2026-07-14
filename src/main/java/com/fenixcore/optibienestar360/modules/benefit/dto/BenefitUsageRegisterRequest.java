package com.fenixcore.optibienestar360.modules.benefit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/ally/benefit-usage}.
 *
 * <p>{@code membershipUuid} is what the ally portal extracts from the
 * validator response — the validator returns the membership UUID for an
 * ACTIVE row, the operator picks the service / fills the rest, the POST
 * lands here.</p>
 *
 * <p>Co-pay is optional but if any side is set the other must be set too
 * (mirror of V24 chk_benefit_usages_copay_paired). The validator at the
 * service layer enforces this with a clean 422.</p>
 */
public record BenefitUsageRegisterRequest(
        @NotNull UUID membershipUuid,
        @NotNull UUID allyUuid,

        UUID allyServiceUuid,                 // optional — non-catalogued usage is allowed
        UUID allyUserUuid,                    // optional — admin-side registrations have null

        // Defaults to today when null; never accept future dates
        @PastOrPresent LocalDate usageDate,

        // Co-pay both NULL or both set (service enforces; mirrors V24 CHECK)
        @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal copayAmount,
        @Pattern(regexp = "^[A-Z]{3}$", message = "{payment.currency.iso}")
        String copayCurrency,

        Map<String, Object> metadata,         // per-ally-type free-form payload
        String notes
) {}
