package com.fenixcore.optibienestar360.modules.media.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.UUID;

/** One image of a gallery as admin and portal see it (hub ADR 0027). */
public record MediaItemDto(
        UUID uuid,
        /** What clients load: the external URL, or {@code publicBaseUrl + key}. */
        String url,
        /** {@code UPLOAD} (a file in R2) or {@code URL} (an external https link). */
        String source,
        String caption,
        int sortOrder,
        /** The image lists use as icon / cover. */
        boolean primary,
        /** Original file name of an upload; {@code null} for a URL. */
        String fileName,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {}
