package com.fenixcore.optibienestar360.modules.onboarding.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.UUID;

/** Retention state of a registration draft after a retain / release call. */
public record DraftRetentionDto(
        UUID uuid,
        boolean retainFromCleanup,
        String retainNote,
        @Display(Display.Kind.DATETIME) Instant retainedAt
) {}
