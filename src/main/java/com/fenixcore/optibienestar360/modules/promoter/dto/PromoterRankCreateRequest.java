package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PromoterRankCreateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z_]{1,40}$", message = "{validation.code.uppercase.long}") String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(1) Integer hierarchyLevel,
        @Min(1) Integer maxSubordinates,
        @Size(max = 200) String description
) {}
