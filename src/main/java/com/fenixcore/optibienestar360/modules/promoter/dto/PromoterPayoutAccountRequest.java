package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code POST} and {@code PUT .../payout-accounts[/{uuid}]}.
 *
 * <p><b>Full-replace semantics</b> on update: the form always sends the whole
 * account and a {@code null}/blank value clears that field (switching the
 * method leaves the old method's fields behind otherwise). Which fields are
 * required depends on the payment method's {@code is_mandatory_*} flags and
 * is checked in the service (422).</p>
 *
 * <p>{@code isDefault} is only honored on create (the first account of an
 * advisor is always the default); on update use
 * {@code PUT .../{uuid}/default}.</p>
 */
public record PromoterPayoutAccountRequest(
        @NotNull UUID paymentMethodUuid,
        @Size(max = 60) String label,

        UUID bankUuid,
        @Pattern(regexp = "^$|^(CORRIENTE|AHORRO)$", message = "{validation.bank_account_type.format}")
        String accountType,
        @Size(max = 34) String accountNumber,
        @Size(max = 120) String accountIdentifier,

        @Size(max = 150) String holderName,
        @Pattern(regexp = "^$|^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String holderDocumentType,
        @Size(max = 20) String holderDocumentNumber,

        @Size(max = 30) String phone,
        @Email @Size(max = 160) String email,

        Boolean isDefault
) {}
