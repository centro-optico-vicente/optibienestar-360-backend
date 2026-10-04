package com.fenixcore.optibienestar360.modules.promotion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPromotionRequest(@NotBlank @Size(max = 500) String reason) {}
