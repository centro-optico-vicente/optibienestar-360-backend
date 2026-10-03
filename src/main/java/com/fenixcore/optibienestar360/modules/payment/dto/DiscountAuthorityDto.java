package com.fenixcore.optibienestar360.modules.payment.dto;

import java.math.BigDecimal;

/**
 * The caller's discount-authority cap (%) — {@code null} means uncapped
 * (back-office staff, not a promoter).
 */
public record DiscountAuthorityDto(BigDecimal maxDiscountPct) {
}
