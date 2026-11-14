package com.fenixcore.optibienestar360.modules.affiliation.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Master-table filters for the admin affiliation-requests inbox (first-class
 * query params next to {@code status} and {@code q}).
 *
 * @param planUuid          the requested plan
 * @param promoterUuid      the advisor the request came through
 * @param paymentMethodUuid the method of the reported inscription payment
 * @param dateFrom          inclusive lower bound on the creation day (Caracas)
 * @param dateTo            inclusive upper bound on the creation day (Caracas)
 * @param unassigned        {@code true} = only requests without an advisor or with the INSTITUCION
 *                          system advisor (ADR LOC-0016)
 */
public record AffiliationRequestListFilter(
        UUID planUuid,
        UUID promoterUuid,
        UUID paymentMethodUuid,
        LocalDate dateFrom,
        LocalDate dateTo,
        Boolean unassigned) {

    /** Pre-{@code unassigned} signature, kept so existing callers and tests compile. */
    public AffiliationRequestListFilter(UUID planUuid, UUID promoterUuid, UUID paymentMethodUuid,
                                        LocalDate dateFrom, LocalDate dateTo) {
        this(planUuid, promoterUuid, paymentMethodUuid, dateFrom, dateTo, null);
    }

    public static AffiliationRequestListFilter none() {
        return new AffiliationRequestListFilter(null, null, null, null, null, null);
    }
}
