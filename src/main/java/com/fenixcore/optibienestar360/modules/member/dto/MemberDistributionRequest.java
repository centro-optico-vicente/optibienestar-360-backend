package com.fenixcore.optibienestar360.modules.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /v1/admin/members/advisor/distribute} and {@code .../distribute/preview}
 * (ADR LOC-0016). Exactly one of {@code memberUuids} (explicit selection) or {@code filter} (every
 * candidate matching it, minus {@code excludeMemberUuids}) is expected. The business rules (at most
 * 2000 members, 1..20 distinct active non-system targets, counts) are checked by the service and
 * answered with 422.
 */
public record MemberDistributionRequest(
        List<@NotNull UUID> memberUuids,
        @Valid MemberCandidateFilter filter,
        List<@NotNull UUID> excludeMemberUuids,
        @NotNull List<@NotNull @Valid Target> targets,
        @NotNull MemberDistributionMode mode,
        @NotBlank @Size(max = 500) String reason
) {

    /** A destination advisor; {@code count} is required (≥ 1) only in {@code COUNT} mode. */
    public record Target(@NotNull UUID promoterUuid, Integer count) {}
}
