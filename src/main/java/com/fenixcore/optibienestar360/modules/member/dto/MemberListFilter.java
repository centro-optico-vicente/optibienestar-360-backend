package com.fenixcore.optibienestar360.modules.member.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Master-table filters for the admin members list, sent as first-class query
 * params (RSQL cannot traverse these associations safely).
 *
 * @param promoterUuid       the member's current advisor
 * @param genderUuid         the person's gender
 * @param cityUuid           the person's city
 * @param occupationUuid     the member's occupation
 * @param corporateContractUuid the corporate contract the member belongs to
 * @param confirmed          {@code true} = confirmed members, {@code false} = pending confirmation
 * @param dateFrom            inclusive lower bound on {@code enrolledAt} (Caracas day)
 * @param dateTo              inclusive upper bound on {@code enrolledAt} (Caracas day)
 */
public record MemberListFilter(
        UUID promoterUuid,
        UUID genderUuid,
        UUID cityUuid,
        UUID occupationUuid,
        UUID corporateContractUuid,
        Boolean confirmed,
        LocalDate dateFrom,
        LocalDate dateTo) {

    /** Pre-date-range signature, kept so existing callers and tests compile. */
    public MemberListFilter(UUID promoterUuid, UUID genderUuid, UUID cityUuid, UUID occupationUuid,
                            UUID corporateContractUuid, Boolean confirmed) {
        this(promoterUuid, genderUuid, cityUuid, occupationUuid, corporateContractUuid, confirmed, null, null);
    }

    public static MemberListFilter none() {
        return new MemberListFilter(null, null, null, null, null, null);
    }
}
