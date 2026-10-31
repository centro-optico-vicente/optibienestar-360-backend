package com.fenixcore.optibienestar360.modules.affiliation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Payment of the plan's inscription fee reported on the public affiliation
 * form ({@code payment} of {@link AffiliationRequestCreateRequest}). Same
 * method/bank fields and per-method mandatory rules as a collection payment
 * ({@code PaymentCreateRequest}); amount and currency are not sent — the
 * server takes the selected plan's inscription fee and its currency. The proof
 * file travels as the {@code support} part of the multipart request.
 */
public record AffiliationPaymentRequest(
        @NotNull UUID paymentMethodUuid,
        UUID bankUuid,
        @Size(max = 80) String identification,
        @Size(max = 40) String bankAccountType,
        @Size(max = 40) String bankAccountCode,
        @Size(max = 120) String bankAccountIdentifier,
        @Size(max = 40) String phone,
        @Size(max = 160) String email,
        @Size(max = 80) String referenceNumber,
        @NotNull @PastOrPresent Instant paymentDate
) {}
