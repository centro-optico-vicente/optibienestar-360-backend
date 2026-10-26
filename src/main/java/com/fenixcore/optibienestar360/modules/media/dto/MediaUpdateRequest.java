package com.fenixcore.optibienestar360.modules.media.dto;

import jakarta.validation.constraints.Size;

/** PATCH semantics: non-null fields applied. {@code primary = true} makes it the primary image (the previous one is unset). */
public record MediaUpdateRequest(
        @Size(max = 200) String caption,
        Boolean primary
) {}
