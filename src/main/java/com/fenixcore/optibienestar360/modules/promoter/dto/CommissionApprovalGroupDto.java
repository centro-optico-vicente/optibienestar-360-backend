package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One promoter node of {@code GET /v1/admin/commissions/approval-queue}
 * (V107, hub plan §4, PR5) — the level-1 (collapsed) row of the 2-level
 * expandable table, carrying the period's total-to-commission for this
 * promoter and every individual commission row underneath.
 *
 * <p>{@link #periodTotal} sums only {@link CommissionApprovalRowDto#checked}
 * rows (PENDING/APPROVED/PAID) — a REJECTED/VOIDED row still appears in
 * {@link #rows} for transparency but never counts toward the total the
 * gerente comercial sees at this level.</p>
 */
public record CommissionApprovalGroupDto(
        UUID promoterUuid,
        String promoterCode,
        String promoterDisplayName,
        @Display(Display.Kind.MONEY) BigDecimal periodTotal,
        String currencyCode,
        List<CommissionApprovalRowDto> rows
) {}
