package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One affiliate in a promoter's portfolio — the drill-down row of
 * {@code GET /v1/promoter/me}. {@code membershipStatus} is the status of the
 * member's <i>currently-active</i> membership (ACTIVE = al día; SUSPENDED /
 * EXPIRED = vencida) or {@code null} when the member has no active membership.
 *
 * <p>Populated directly by a JPQL constructor expression
 * ({@code MemberRepository.findPromoterPortfolio}); its constructor signature is
 * therefore load-bearing — keep the field order/types in sync with that query.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromoterMemberRow(
        UUID memberUuid,
        String memberName,
        String membershipStatus,
        LocalDate nextDueDate,
        BigDecimal monthlyFee
) {}
