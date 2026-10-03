package com.fenixcore.optibienestar360.modules.member.event;

/**
 * A member was moved to a new (human) promoter. Carries plain values resolved
 * inside the reassigning transaction, so the after-commit notifier never
 * touches lazy associations.
 */
public record MemberPromoterReassignedEvent(
        String memberEmail,
        String memberFullName,
        String memberLocale,
        String promoterName,
        String promoterPhone,
        String promoterEmail
) {
}
