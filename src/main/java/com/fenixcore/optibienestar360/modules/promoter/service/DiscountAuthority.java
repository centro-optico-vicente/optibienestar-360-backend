package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;

import java.math.BigDecimal;

/**
 * Discount-authority matrix: the most a promoter may discount a payment, as a
 * percentage of its amount. Each axis — the promoter's rank (cargo) and type —
 * may set a cap ({@code max_discount_pct}); the effective cap is the most
 * restrictive of the ones defined, so the rank grants authority by hierarchy
 * level while the type can only narrow it. Neither defined = 0% (a promoter
 * cannot discount until a cap is configured). Users with {@code
 * ALLOWS_DISCOUNT} who are not promoters (back-office staff) are uncapped —
 * callers simply skip the check for them.
 */
public final class DiscountAuthority {

    private DiscountAuthority() {
    }

    public static BigDecimal maxDiscountPct(Promoter promoter) {
        BigDecimal byRank = promoter.getRank() != null ? promoter.getRank().getMaxDiscountPct() : null;
        BigDecimal byType = promoter.getPromoterType() != null ? promoter.getPromoterType().getMaxDiscountPct() : null;
        if (byRank == null && byType == null) {
            return BigDecimal.ZERO;
        }
        if (byRank == null) return byType;
        if (byType == null) return byRank;
        return byRank.min(byType);
    }
}
