package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

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
 * therefore load-bearing — keep the field order/types in sync with that query.
 * Scalars carry a localized {@code _Display} sibling (ADR 0014).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromoterMemberRow(
        UUID memberUuid,
        String memberName,
        @Display(value = Display.Kind.ENUM, enumScope = "membership.status") String membershipStatus,
        @Display(Display.Kind.DATE) LocalDate nextDueDate,
        @Display(Display.Kind.MONEY) BigDecimal monthlyFee
) {}
