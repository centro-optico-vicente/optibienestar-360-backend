package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Holder person of an ally as shown to staff (V209, hub ADR 0031). Admin-only:
 * the public directory never exposes the document or the birth date.
 */
public record AllyHolderPersonDto(
        UUID uuid,
        String documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String fullName,
        @Display(Display.Kind.DATE) LocalDate birthDate,
        String phone,
        String email
) {}
