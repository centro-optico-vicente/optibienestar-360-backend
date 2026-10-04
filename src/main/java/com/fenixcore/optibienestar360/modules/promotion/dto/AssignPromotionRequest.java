package com.fenixcore.optibienestar360.modules.promotion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Body of {@code POST /v1/admin/memberships/{uuid}/promotion}; {@code code} is required only when the promotion demands one. */
public record AssignPromotionRequest(
        @NotNull UUID promotionUuid,
        @Size(max = 20) String code
) {}
