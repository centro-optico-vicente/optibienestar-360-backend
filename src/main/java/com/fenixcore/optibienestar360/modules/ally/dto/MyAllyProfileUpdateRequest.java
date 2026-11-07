package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Portal profile edit ({@code PUT /v1/me/allies/{allyUuid}/profile}, hub ADR
 * 0036). PATCH semantics: {@code null} = unchanged, blank text = cleared.
 * The other segment's fields are rejected (422
 * {@code ally.segment.field_not_allowed}); {@code name} only while the request
 * is PENDING (422 {@code ally.profile.name_locked}).
 */
public record MyAllyProfileUpdateRequest(
        @Size(max = 200) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @Size(max = 30) String whatsapp,
        @Size(max = 255) String website,
        @Size(max = 255) String instagram,
        @Size(max = 255) String facebook,
        @Size(max = 2000) String address,
        UUID cityUuid,
        @Size(max = 500) String googleMapsUrl,
        @Size(max = 4000) String description,

        // B2B
        @Size(max = 200) String tradeName,
        @Size(max = 100) String commercialZone,
        @Min(1800) @Max(2100) Short foundedYear,
        @PositiveOrZero Integer cashRegistersCount,
        @PositiveOrZero Integer branchesCount,
        Boolean hasPos,
        @Size(max = 100) String representativePosition,

        // B2P
        @Size(max = 200) String hostName,
        @Size(max = 20) String officeNumber,
        @Size(max = 30) String appointmentPhone,
        @Size(max = 150) String assistantName
) {}
