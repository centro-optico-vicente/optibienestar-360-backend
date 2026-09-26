package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /v1/admin/competitive-commission-awards/{uuid}/void}. */
public record CompetitiveAwardVoidRequest(@NotBlank String reason) {
}
