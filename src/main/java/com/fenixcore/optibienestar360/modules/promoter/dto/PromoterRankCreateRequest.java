package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code hierarchyLevel} is a desired position, not a hard requirement: if it
 * collides with an existing active rank, {@code PromoterRankService.create}
 * slots the new rank in the gap immediately below the colliding one (or
 * renumbers all active ranks in gaps of 10 first, if no gap is left) instead
 * of rejecting the request (V111).
 */
public record PromoterRankCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,40}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(1) Integer hierarchyLevel,
        @Min(1) Integer maxSubordinates,
        @Size(max = 200) String description,
        /** Immediate superior rank's uuid (V111) — optional, null means top of the chain. */
        UUID parentRankUuid
) {}
