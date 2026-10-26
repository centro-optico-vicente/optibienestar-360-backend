package com.fenixcore.optibienestar360.modules.media.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** The owner's whole active gallery, in the new display order. */
public record MediaReorderRequest(@NotEmpty List<UUID> uuids) {
}
