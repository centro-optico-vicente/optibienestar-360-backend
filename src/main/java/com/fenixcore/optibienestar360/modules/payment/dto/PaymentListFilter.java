package com.fenixcore.optibienestar360.modules.payment.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Master-table filters for the admin payments list. Each one is a first-class
 * query param rather than an RSQL clause: the method and bank live on
 * {@code payment_lines} (a collection) and RSQL cannot express "any line
 * matches" safely, and the allow-list validator only understands scalar ops.
 *
 * @param memberUuid        the affiliate — matches payments whose payer or credited beneficiary is that member's person
 * @param paymentMethodUuid a line's method (e.g. transfer, cash)
 * @param bankUuid          a line's bank
 * @param categoryUuid      the payment reason (header category)
 * @param promoterUuid      the related advisor
 * @param dateFrom          inclusive lower bound on {@code paymentDate} (Caracas day)
 * @param dateTo            inclusive upper bound on {@code paymentDate} (Caracas day)
 */
public record PaymentListFilter(
        UUID memberUuid,
        UUID paymentMethodUuid,
        UUID bankUuid,
        UUID categoryUuid,
        UUID promoterUuid,
        LocalDate dateFrom,
        LocalDate dateTo) {

    public static PaymentListFilter none() {
        return new PaymentListFilter(null, null, null, null, null, null, null);
    }
}
