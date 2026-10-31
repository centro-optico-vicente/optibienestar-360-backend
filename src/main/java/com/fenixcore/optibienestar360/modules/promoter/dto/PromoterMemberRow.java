package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One affiliate in a promoter's portfolio — the drill-down row of
 * {@code GET /v1/me/advisor}, which doubles as the advisor's "my sales" list
 * ({@code enrolledAt} is the sale date). {@code membershipStatus} is the status
 * of the member's <i>currently-active</i> membership (ACTIVE = al día;
 * SUSPENDED / EXPIRED = vencida) or {@code null} when the member has no active
 * membership.
 *
 * <p>{@code paymentStatus} is what the advisor acts on, derived from the
 * membership status and the due date:</p>
 * <ul>
 *   <li>{@code UP_TO_DATE} — ACTIVE and not past its due date.</li>
 *   <li>{@code DUE} — ACTIVE but past its due date: hasn't paid yet, still in
 *       the grace period.</li>
 *   <li>{@code OVERDUE} — SUSPENDED / EXPIRED: moroso.</li>
 *   <li>{@code NO_MEMBERSHIP} — no active membership.</li>
 * </ul>
 * {@code daysOverdue} counts the days past {@code nextDueDate} (0 when not past it).
 *
 * <p>Populated directly by a JPQL constructor expression
 * ({@code MemberRepository.findPromoterPortfolio}) through the 10-argument
 * constructor; keep its field order/types in sync with that query. Scalars
 * carry a localized {@code _Display} sibling (ADR 0014).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromoterMemberRow(
        UUID memberUuid,
        String memberName,
        String documentNumber,
        String phone,
        String planName,
        @Display(Display.Kind.DATE) LocalDate enrolledAt,
        @Display(value = Display.Kind.ENUM, enumScope = "membership.status") String membershipStatus,
        @Display(Display.Kind.DATE) LocalDate nextDueDate,
        @Display(Display.Kind.DATE) LocalDate lastPaidThrough,
        @Display(Display.Kind.MONEY) BigDecimal monthlyFee,
        String paymentStatus,
        Integer daysOverdue
) {

    /** JPQL constructor ({@code findPromoterPortfolio}); derives the payment status. */
    public PromoterMemberRow(UUID memberUuid, String memberName, String documentNumber, String phone,
                             String planName, LocalDate enrolledAt, String membershipStatus,
                             LocalDate nextDueDate, LocalDate lastPaidThrough, BigDecimal monthlyFee) {
        this(memberUuid, memberName, documentNumber, phone, planName, enrolledAt, membershipStatus, nextDueDate,
                lastPaidThrough, monthlyFee, paymentStatus(membershipStatus, nextDueDate), daysOverdue(nextDueDate));
    }

    /** Status + due date only — what the dashboard counters need. */
    public PromoterMemberRow(UUID memberUuid, String memberName, String membershipStatus,
                             LocalDate nextDueDate, BigDecimal monthlyFee) {
        this(memberUuid, memberName, null, null, null, null, membershipStatus, nextDueDate, null, monthlyFee);
    }

    static String paymentStatus(String membershipStatus, LocalDate nextDueDate) {
        if ("ACTIVE".equals(membershipStatus)) {
            return nextDueDate != null && nextDueDate.isBefore(LocalDate.now()) ? "DUE" : "UP_TO_DATE";
        }
        if ("SUSPENDED".equals(membershipStatus) || "EXPIRED".equals(membershipStatus)) {
            return "OVERDUE";
        }
        return "NO_MEMBERSHIP";
    }

    static Integer daysOverdue(LocalDate nextDueDate) {
        if (nextDueDate == null) return null;
        long days = ChronoUnit.DAYS.between(nextDueDate, LocalDate.now());
        return (int) Math.max(days, 0);
    }
}
