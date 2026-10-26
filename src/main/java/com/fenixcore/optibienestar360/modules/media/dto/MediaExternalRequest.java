package com.fenixcore.optibienestar360.modules.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Adds an image by external link ({@code https} only). */
public record MediaExternalRequest(
        @NotBlank @Size(max = 500) String url,
        @Size(max = 200) String caption
) {}
