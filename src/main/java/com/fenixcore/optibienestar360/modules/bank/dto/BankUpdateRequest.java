package com.fenixcore.optibienestar360.modules.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code code} is the immutable natural key — not editable here, same as {@code Currency.code}. */
public record BankUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 60) String shortName,
        @NotBlank @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}") String taxDocumentType,
        @NotBlank @Size(max = 20) String taxDocumentNumber,
        Boolean active
) {}
