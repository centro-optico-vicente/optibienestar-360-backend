package com.fenixcore.optisaludplus.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Breaking change in v2: {@code fullName} (single string) removed in favor of
 * the 4 explicit name parts that map directly to {@code persons} columns
 * (LATAM convention). Frontend admin form must send {@code firstName} +
 * {@code lastName} as required, plus optional {@code middleName} and
 * {@code secondLastName}.
 *
 * <p>{@code documentType} + {@code documentNumber} are now both required
 * (cédula is the canonical identity for persons hub lookups via
 * {@code PersonService.findOrCreate}). RIF is optional.</p>
 */
public record AdminCreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @NotBlank @Size(max = 50) String lastName,
        @Size(max = 50) String secondLastName,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        @NotBlank @Size(max = 20) String documentNumber,
        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}") String taxDocumentType,
        @Size(max = 20) String taxDocumentNumber,
        @Size(max = 30) String phone,
        @NotEmpty List<UUID> roleIds
) {}
