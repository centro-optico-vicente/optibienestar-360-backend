package com.fenixcore.optibienestar360.modules.membership.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/plans} (list) and
 * {@code GET /v1/admin/plans/{uuid}} (detail). Flat record — Plan has no
 * nested relationships at this layer (memberships hang off the inverse FK
 * and are queried separately on the affiliate detail page). Scalars carry a
 * localized {@code _Display} sibling (ADR 0014), matching {@link PublicPlanDto}.
 */
public record PlanDto(
        UUID uuid,
        String code,
        String name,
        String description,
        @Display(Display.Kind.ENUM) PlanType type,

        // Pricing
        @Display(Display.Kind.MONEY) BigDecimal inscriptionFee,
        @Display(Display.Kind.MONEY) BigDecimal monthlyFee,

        // Beneficiaries
        int includedBeneficiaries,
        Integer maxBeneficiaries,
        @Display(Display.Kind.MONEY) BigDecimal extraBeneficiaryInscriptionFee,

        int gracePeriodDays,

        // Publishing
        @Display(Display.Kind.BOOLEAN) boolean published,
        @Display(Display.Kind.DATETIME) Instant publishedAt,

        // Audit
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "plan.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {}
