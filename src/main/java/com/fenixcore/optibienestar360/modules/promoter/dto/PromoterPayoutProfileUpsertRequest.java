package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/promoters/{promoterUuid}/payout-profile}.
 *
 * <p><b>Full-replace semantics</b> (unlike the PATCH-style medical record):
 * the form always sends the whole profile, and a {@code null}/blank value
 * clears that field — an advisor who stops using Ubii must be able to drop
 * it. Every field is optional; {@code accountNumber} must be exactly 20
 * digits when present, and the holder cédula/RIF comes as a pair.</p>
 */
public record PromoterPayoutProfileUpsertRequest(
        UUID bankUuid,
        @Pattern(regexp = "^$|^[0-9]{20}$", message = "{validation.account_number.format}")
        String accountNumber,
        @Pattern(regexp = "^$|^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String accountHolderDocumentType,
        @Size(max = 20) String accountHolderDocumentNumber,

        @Size(max = 30) String mobilePaymentPhone,
        UUID mobilePaymentBankUuid,

        @Size(max = 60) String ubiiAccount,
        @Size(max = 150) String ubiiHolderName
) {}
