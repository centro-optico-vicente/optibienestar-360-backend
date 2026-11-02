package com.fenixcore.optibienestar360.modules.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Self-service view of the caller's advisor ("Mi asesor", V201). {@code exists=false} when the user has
 * no member record (an advisor or ally who never enrolled); {@code advisorUuid} is null when nobody
 * advises them yet. {@code history} is newest first.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyAdvisorDto(
        boolean exists,
        UUID advisorUuid,
        String advisorName,
        List<Entry> history
) {
    /** One change of advisor; {@code fromName} is null for the first assignment. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Entry(
            String fromName,
            String toName,
            @Display(Display.Kind.DATETIME) Instant assignedAt
    ) {}
}
