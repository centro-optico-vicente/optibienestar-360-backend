package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyServiceReviewLog;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of an ally-service review history — shared by the admin and the
 * ally-owner log endpoints (the service scopes who may read it, the shape is
 * identical). {@code actorUuid} is null for system-driven transitions.
 *
 * <p>Scalars carry a localized {@code _Display} sibling (ADR 0014).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AllyServiceReviewLogDto(
        @Display(Display.Kind.ENUM) ReviewStatus fromStatus,
        @Display(Display.Kind.ENUM) ReviewStatus toStatus,
        UUID actorUuid,
        @Display(Display.Kind.DATETIME) Instant actionAt,
        String comment
) {

    public static AllyServiceReviewLogDto from(AllyServiceReviewLog e) {
        return new AllyServiceReviewLogDto(
                e.getFromStatus(),
                e.getToStatus(),
                e.getActor() != null ? e.getActor().getUuid() : null,
                e.getActionAt(),
                e.getComment());
    }
}
