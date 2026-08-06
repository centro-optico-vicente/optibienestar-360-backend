package com.fenixcore.optibienestar360.modules.promoter.dto;

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
 * {@code userUuid} is required. The linked {@code Person} is derived
 * server-side from {@code user.getPerson()} (User → Person is a mandatory
 * 1:1 FK), so the client never supplies a {@code personUuid} directly. The
 * system INSTITUCION row is seeded in V25 and cannot be replicated through
 * the API (the partial UNIQUE in V25 enforces "at most one" at the DB level;
 * the service surfaces a clean 422 if anyone tries).</p>
 *
 * <p>{@code referralCode} is <b>optional</b> (v2 PDF #4 "el promotor genera su
 * código único"): omit it (or send blank) and the service auto-generates a
 * short 6-char code; supply one to pick a vanity code. Either way it follows the
 * UPPER_ALPHANUM pattern the V25 CHECK enforces, and the service rejects codes
 * that collide with an existing {@code promoters}/{@code members.referral_code}
 * (cross-table uniqueness, service-side per V27 design). The {@code ^$} branch
 * lets a blank value through validation so the service can auto-generate.</p>
 */
public record PromoterCreateRequest(
        @NotBlank @Size(max = 120) String displayName,
        String description,

        @Pattern(regexp = "^$|^[A-Z0-9-]{4,20}$", message = "{promoter.referral_code.format}")
        String referralCode,

        @NotNull UUID userUuid,

        @Email @Size(max = 320) String email,
        @Size(max = 30) String phone,

        UUID promoterTypeUuid
) {}
