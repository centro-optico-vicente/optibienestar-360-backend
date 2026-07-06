package com.fenixcore.optisaludplus.modules.member.dto;

import com.fenixcore.optisaludplus.core.validation.MinimumAge;
import com.fenixcore.optisaludplus.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/members/{uuid}}. PATCH semantics — only
 * non-null fields are applied. Person fields update the linked persons row;
 * member-specific fields update the member row.
 *
 * <p>{@link MinimumAge} still validates birth date when present so an admin
 * can't accidentally downgrade a titular to a minor's date — beneficiaries
 * are the only role that allows minors and they have their own DTO.</p>
 */
public record MemberUpdateRequest(
        // ─── Person fields ─────────────────────────────────────────────────
        @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @Size(max = 50) String lastName,
        @Size(max = 50) String secondLastName,

        @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}")
        String documentType,
        @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,

        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String taxDocumentType,
        @Size(max = 20) String taxDocumentNumber,

        @MinimumAge LocalDate birthDate,

        UUID genderUuid,
        UUID maritalStatusUuid,

        // ─── Inscription-form demographics (planilla) ──────────────────────
        @Size(max = 120) String birthplace,
        @PositiveOrZero @Max(50) Integer numberOfChildren,
        @Size(max = 210) String spouseName,

        @Size(max = 30) String phone,
        @Size(max = 30) String landlinePhone,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "^(es|es-VE|en)$", message = "{validation.locale.allowed}") String locale,

        String address,
        UUID cityUuid,

        // ─── Member-specific ───────────────────────────────────────────────
        UUID occupationUuid,
        @Size(max = 150) String employerName,
        @Size(max = 100) String jobPosition,
        String employerAddress,
        LocalDate enrolledAt,
        String notes,
        Boolean active,
        String status
) {}
