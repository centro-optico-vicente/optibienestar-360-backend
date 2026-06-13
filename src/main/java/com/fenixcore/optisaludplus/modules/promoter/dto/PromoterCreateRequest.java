package com.fenixcore.optisaludplus.modules.promoter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/promoters}.
 *
 * <p>Only HUMAN promoters can be created via this endpoint —
 * {@code userUuid} + {@code personUuid} are required. The system
 * INSTITUCION row is seeded in V25 and cannot be replicated through the
 * API (the partial UNIQUE in V25 enforces "at most one" at the DB level;
 * the service surfaces a clean 422 if anyone tries).</p>
 *
 * <p>{@code referralCode} follows the same UPPER_ALPHANUM pattern the V25
 * CHECK enforces. The service additionally rejects codes that collide
 * with an existing {@code members.referral_code} (cross-table
 * uniqueness, service-side per V27 design).</p>
 */
public record PromoterCreateRequest(
        @NotBlank @Size(max = 120) String displayName,
        String description,

        @NotBlank
        @Pattern(regexp = "^[A-Z0-9-]{4,20}$", message = "{promoter.referral_code.format}")
        String referralCode,

        @NotNull UUID userUuid,
        @NotNull UUID personUuid,

        @Email @Size(max = 320) String email,
        @Size(max = 30) String phone
) {}
