package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Asks for an access link; {@code identifier} is the email or the national ID. */
public record MagicLinkRequest(
        @NotBlank @Size(max = 255) String identifier
) {}
