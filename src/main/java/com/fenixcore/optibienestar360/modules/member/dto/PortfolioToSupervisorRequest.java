package com.fenixcore.optibienestar360.modules.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Payload for {@code POST /v1/admin/members/promoter/portfolio-to-supervisor} — roll a promoter's portfolio up. */
public record PortfolioToSupervisorRequest(
        @NotNull UUID sourcePromoterUuid,
        @NotBlank @Size(max = 2000) String reason
) {
}
