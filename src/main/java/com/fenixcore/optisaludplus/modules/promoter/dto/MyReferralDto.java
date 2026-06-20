package com.fenixcore.optisaludplus.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Affiliate-side view of a single referral row, returned by
 * {@code GET /v1/me/referrals}. The caller is always the REFERRER, so
 * referrer identity is implicit; only the {@link #referredMemberUuid}
 * (the friend who enrolled) needs to be echoed.
 *
 * <p>{@code referredMemberUuid} and {@code referredMemberName} are null
 * while the referral is {@code PENDING_ENROLLMENT} or {@code EXPIRED}
 * (the friend never actually enrolled).</p>
 *
 * <p>Reward snapshot is echoed verbatim from the row — when v2
 * {@code referral_programs} introduces DB-driven config, historical
 * rows keep displaying the original reward they were promised.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyReferralDto(
        UUID uuid,
        String status,
        String referralCode,

        UUID referredMemberUuid,
        String referredMemberName,

        Instant enrolledAt,
        Instant expiresAt,

        BigDecimal rewardPct,
        BigDecimal rewardFlatAmount,
        String rewardCurrency,

        UUID rewardPaymentUuid,
        Instant rewardGrantedAt,

        Instant createdAt
) {}
