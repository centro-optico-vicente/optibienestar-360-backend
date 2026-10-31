package com.fenixcore.optibienestar360.modules.benefit.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
 * Payload for {@code POST /v1/me/allies/benefit-usages}.
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

        // optional — non-catalogued usage is allowed; allyServiceUuid accepted during the ADR 0026 transition
        @JsonAlias("allyServiceUuid") UUID allyProductUuid,
        UUID allyUserUuid,                    // optional — admin-side registrations have null

        /**
         * Rubro del consumo. Opcional SOLO cuando viene {@code allyProductUuid}:
         * en ese caso el producto de la oferta ya trae su categoría y el servidor la copia.
         * Sin oferta es obligatorio — es la condición que garantiza que todo
         * consumo entre en el reporte por rubro (V158).
         */
        @JsonAlias("serviceCategoryUuid") UUID productCategoryUuid,

        // Defaults to today when null; never accept future dates
        @PastOrPresent LocalDate usageDate,

        // Co-pay both NULL or both set (service enforces; mirrors V24 CHECK)
        @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal copayAmount,
        @Pattern(regexp = "^[A-Z]{3}$", message = "{payment.currency.iso}")
        String copayCurrency,

        // Valor consumido — ambos NULL o ambos con valor (el servicio lo valida,
        // espeja chk_benefit_usages_consumption_paired de V158)
        @Digits(integer = 8, fraction = 2) @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal consumptionAmount,
        @Pattern(regexp = "^[A-Z]{3}$", message = "{payment.currency.iso}")
        String consumptionCurrency,

        Map<String, Object> metadata,         // per-ally-type free-form payload
        String notes,

        // Who consumed (V195): one of the membership's active beneficiaries; null = the holder
        UUID beneficiaryUuid
) {}
