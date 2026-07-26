package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /v1/promoter/me/contacts/{memberUuid}/reminder}
 * (v2 PDF 2.b). The promoter records that they contacted the affiliate about a
 * due payment. The note is optional — logging the outreach at all is the point.
 */
public record ReminderRequest(
        @Size(max = 2000) String note
) {}
