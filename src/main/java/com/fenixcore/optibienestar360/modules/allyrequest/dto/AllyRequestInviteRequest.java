package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Invitation of an imported ally's contact to the portal (hub ADR 0036). All
 * optional: the cédula and names are needed only when the ally has no holder
 * person yet; {@code email} overrides the ally's contact email.
 */
public record AllyRequestInviteRequest(
        @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Email @Size(max = 255) String email
) {}
