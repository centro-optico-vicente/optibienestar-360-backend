package com.fenixcore.optibienestar360.modules.affiliation.dto;

import jakarta.validation.constraints.Size;

/**
 * {@code POST /v1/me/affiliation-request} — step 1 of the affiliation for
 * someone who already has an account: identity and contact come from the
 * account, so only the optional referral code is asked.
 *
 * @param referralCode  advisor code that referred the person (optional)
 * @param refreshToken  the caller's refresh token, revoked when the session
 *                      switches to the pending role (optional)
 */
public record MyAffiliationStartRequest(
        @Size(max = 20) String referralCode,
        String refreshToken
) {}
