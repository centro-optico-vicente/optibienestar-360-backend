package com.fenixcore.optibienestar360.modules.campaign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of a manual slot confirm / release (V260, hub ADR 0038 §9): the note is required and kept on the slot. */
public record PromotionSlotNoteRequest(
        @NotBlank(message = "{promotion_slot.note.required}") @Size(max = 500) String note) {
}
