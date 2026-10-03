package com.fenixcore.optibienestar360.modules.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Payload for {@code POST /v1/admin/members/promoter/bulk-assign} — portfolio reassignment to one promoter. */
public record BulkAssignPromoterRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> memberUuids,
        @NotNull UUID promoterUuid,
        @NotBlank @Size(max = 2000) String reason
) {
}
