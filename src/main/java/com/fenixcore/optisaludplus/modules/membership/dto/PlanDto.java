package com.fenixcore.optisaludplus.modules.membership.dto;

import com.fenixcore.optisaludplus.modules.membership.entity.Plan.PlanType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/plans} (list) and
 * {@code GET /v1/admin/plans/{uuid}} (detail). Flat record — Plan has no
 * nested relationships at this layer (memberships hang off the inverse FK
 * and are queried separately on the affiliate detail page).
 */
public record PlanDto(
        UUID uuid,
        String code,
        String name,
        String description,
        PlanType type,

        // Pricing
        BigDecimal inscriptionFee,
        BigDecimal monthlyFee,

        // Beneficiaries
        int includedBeneficiaries,
        Integer maxBeneficiaries,
        BigDecimal extraBeneficiaryInscriptionFee,

        int gracePeriodDays,

        // Publishing
        boolean published,
        Instant publishedAt,

        // Audit
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
