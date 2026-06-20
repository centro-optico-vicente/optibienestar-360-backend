package com.fenixcore.optisaludplus.modules.promoter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code POST /v1/admin/referral-codes}. Echoes the
 * resolved member identity, the previous code (for admin UI confirmation
 * "you replaced X with Y" — null on first issuance), the new code, and
 * a wall-clock stamp of when the issuance executed.
 *
 * <p>{@code generated=true} when the service auto-generated the code;
 * {@code false} when the admin supplied a custom code (vanity flow).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReferralCodeIssueResponse(
        UUID memberUuid,
        String memberFullName,
        String previousCode,
        String referralCode,
        boolean generated,
        Instant issuedAt
) {}
