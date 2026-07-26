package com.fenixcore.optibienestar360.modules.corporate.dto;

import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Payload for {@code POST /v1/admin/corporate-contracts/{uuid}/members} — bulk
 * enrollment. Each entry is a full {@link MemberCreateRequest} (the same shape
 * the single-member admin endpoint accepts), so a CSV import maps row-for-row
 * onto this list. {@code @Valid} cascades bean validation into every entry, so
 * a single malformed row 400s the whole batch before any DB work happens.
 *
 * <p>The batch runs in one transaction: rows whose person is already enrolled
 * are skipped (reported as {@code SKIPPED_DUPLICATE}); any structural failure
 * (unknown catalog UUID, etc.) rolls the whole batch back.</p>
 */
public record CorporateMemberBulkRequest(
        @NotEmpty @Valid List<MemberCreateRequest> members
) {}
