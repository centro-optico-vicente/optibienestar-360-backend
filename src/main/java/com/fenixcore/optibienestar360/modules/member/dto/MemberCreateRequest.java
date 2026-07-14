package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.core.validation.MinimumAge;
import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/admin/members}.
 *
 * <p>Creates the Member <em>and</em> backfills its {@link
 * com.fenixcore.optibienestar360.modules.person.entity.Person Person} hub row
 * if it doesn't exist yet — Person fields come inline here so the admin can
 * affiliate a brand-new human in a single call. If a Person with the same
 * cédula already exists (e.g. the affiliate was previously a User or a
 * Beneficiary under a different titular), the service reuses it via
 * {@code PersonService.findOrCreate} instead of duplicating.</p>
 *
 * <p>Custom validations: {@link VenezuelanDocumentNumber} on the cédula
 * digits, {@link MinimumAge} default 18 on the birth date (titular must be
 * adult — beneficiaries can be minors).</p>
 */
public record MemberCreateRequest(
        // ─── Person (identity hub) — required for the titular ──────────────
        @NotBlank @Size(max = 50) String firstName,
        @Size(max = 50) String middleName,
        @NotBlank @Size(max = 50) String lastName,
        @Size(max = 50) String secondLastName,

        @NotBlank @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}")
        String documentType,

        @NotBlank @Size(max = 20) @VenezuelanDocumentNumber
        String documentNumber,

        @Pattern(regexp = "^[JVEGP]$", message = "{validation.tax_document_type.format}")
        String taxDocumentType,
        @Size(max = 20) String taxDocumentNumber,

        @NotNull @MinimumAge LocalDate birthDate,

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
        String notes
) {}
