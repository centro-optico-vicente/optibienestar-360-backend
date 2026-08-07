package com.fenixcore.optibienestar360.modules.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/members/{uuid}/assign-promoter} (v2 PDF 2.a).
 * Reassigns the permanent member↔promoter link, or links a member that was
 * enrolled without one. Exactly one of {@code promoterUuid} / {@code
 * referralCode} must be given — the admin either picks a promoter directly or
 * enters the promoter's referral code (e.g. the code a promoter shares
 * verbally, resolved server-side the same way {@code PromoterResolver} does
 * at enrollment). {@code reason} is mandatory — the link is a permanent
 * attribution and every change is audited, so the operator must state why.
 */
public record AssignPromoterRequest(
        UUID promoterUuid,
        @Size(max = 20) String referralCode,
        @NotBlank @Size(max = 2000) String reason
) {
    @AssertTrue(message = "member.promoter.target_required")
    public boolean isTargetProvided() {
        boolean hasUuid = promoterUuid != null;
        boolean hasCode = referralCode != null && !referralCode.isBlank();
        return hasUuid ^ hasCode;
    }
}
