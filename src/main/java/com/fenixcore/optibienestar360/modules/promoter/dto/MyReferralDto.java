package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Affiliate-side view of a single referral row, returned by
 * {@code GET /v1/me/referrals}. The caller is always the REFERRER.
 *
 * <p>{@code referredMemberUuid} / {@code referredMemberName} are null while
 * the referral is {@code PENDING_ENROLLMENT} or {@code EXPIRED}. Reward
 * snapshot is echoed verbatim from the row. Scalars carry a localized
 * {@code _Display} sibling (hub ADR 0014).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyReferralDto(
        UUID uuid,
        @Display(value = Display.Kind.ENUM, enumScope = "referral.status") String status,
        String referralCode,

        UUID referredMemberUuid,
        String referredMemberName,

        @Display(Display.Kind.DATETIME) Instant enrolledAt,
        @Display(Display.Kind.DATETIME) Instant expiresAt,

        @Display(Display.Kind.NUMBER) BigDecimal rewardPct,
        @Display(Display.Kind.MONEY) BigDecimal rewardFlatAmount,
        String rewardCurrency,

        UUID rewardPaymentUuid,
        @Display(Display.Kind.DATETIME) Instant rewardGrantedAt,

        @Display(Display.Kind.DATETIME) Instant createdAt
) {}
