package com.fenixcore.optisaludplus.modules.promoter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/referral-codes}. Admin tool for
 * issuing or regenerating the affiliate referral code on an existing
 * member.
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>Auto-generate</b> (default — {@code customCode} null/blank):
 *       service produces a unique 8-char UPPER alphanumeric tag, with
 *       cross-table collision retry against both {@code promoters} and
 *       {@code members}.</li>
 *   <li><b>Custom / vanity</b> ({@code customCode} provided): admin
 *       supplies a specific code. Must match the same format the V25
 *       CHECK enforces on promoters ({@code ^[A-Z0-9-]{4,20}$}) so a
 *       member code is interchangeable in the resolver. Pre-checked
 *       against both tables — collision surfaces as 422 rather than
 *       letting the V27 partial UNIQUE fire raw.</li>
 * </ul>
 *
 * <p>Idempotency: if {@code customCode} matches the member's current
 * code, the service returns the existing code unchanged (no DB write).</p>
 */
public record ReferralCodeIssueRequest(
        @NotNull UUID memberUuid,

        @Pattern(regexp = "^[A-Z0-9-]{4,20}$",
                 message = "{referral_code.format}")
        @Size(max = 20) String customCode
) {}
