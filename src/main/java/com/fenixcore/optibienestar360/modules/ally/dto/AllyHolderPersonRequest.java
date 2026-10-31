package com.fenixcore.optibienestar360.modules.ally.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Holder person of an ally (V211, hub ADR 0031): the professional of a B2P
 * ally or the representative who signs for a B2B ally.
 *
 * <p>Find-or-create by cédula ({@code documentType} + {@code documentNumber}):
 * an existing person is linked as-is and only its empty fields are filled
 * from this payload — the same human may already be a member, advisor or the
 * holder of another site; a new person requires {@code firstName} and
 * {@code lastName} ({@code persons} NOT NULL columns).</p>
 */
public record AllyHolderPersonRequest(
        @NotBlank @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        @NotBlank @Size(max = 20) String documentNumber,
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Past LocalDate birthDate,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email
) {}
