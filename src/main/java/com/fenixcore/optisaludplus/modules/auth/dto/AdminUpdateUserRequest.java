package com.fenixcore.optisaludplus.modules.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Partial update of User + its Person. All fields nullable; only those
 * present are applied (PATCH-style semantics under PUT). Demographic fields
 * (name parts, document, phone, locale) update the underlying Person row;
 * {@code status} + {@code active} + {@code roleIds} update the User.
 *
 * <p>Breaking change in v2: same as AdminCreate — {@code fullName} replaced
 * by the 4 atomic name parts. Frontend must adapt.</p>
 */
public record AdminUpdateUserRequest(
        @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @Size(max = 50) String lastName,
        @Size(max = 50) String secondLastName,
        @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        @Size(max = 20) String documentNumber,
        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}") String taxDocumentType,
        @Size(max = 20) String taxDocumentNumber,
        @Size(max = 30) String phone,
        @Pattern(regexp = "^(es|es-VE|en)$", message = "{validation.locale.allowed}") String locale,
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|LOCKED)$",
                 message = "{validation.user_status.allowed_values}") String status,
        Boolean active,
        List<UUID> roleIds
) {}
