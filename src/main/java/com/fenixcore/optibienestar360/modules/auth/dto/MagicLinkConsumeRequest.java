package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** The raw token carried by the access link. */
public record MagicLinkConsumeRequest(
        @NotBlank String token
) {}
