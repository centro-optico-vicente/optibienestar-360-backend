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
 */
public record AffiliationRequestListFilter(
        UUID planUuid,
        UUID promoterUuid,
        UUID paymentMethodUuid,
        LocalDate dateFrom,
        LocalDate dateTo) {

    public static AffiliationRequestListFilter none() {
        return new AffiliationRequestListFilter(null, null, null, null, null);
    }
}
