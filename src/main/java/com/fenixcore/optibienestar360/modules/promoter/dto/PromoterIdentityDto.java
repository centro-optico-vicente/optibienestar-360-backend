package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Identity block of the advisor dossier (section II "Identidad"), read from
 * the {@code persons} row linked through {@code promoters.person_id} (which
 * is the linked user's person — see {@code PromoterCreateRequest}).
 *
 * <p>Only populated on {@code GET /v1/admin/promoters/{uuid}} (detail); the
 * list returns {@code identity = null} to avoid loading the person's
 * catalog FKs (marital status, city) for every row.</p>
 */
public record PromoterIdentityDto(
        UUID personUuid,
        String firstName,
        String lastName,
        String fullName,
        String documentType,
        String documentNumber,
        @Display(Display.Kind.DATE) LocalDate birthDate,
        @Display DisplayRef maritalStatus,
        String phone,
        String landlinePhone,
        String email,
        String address,
        @Display DisplayRef city
) {}
