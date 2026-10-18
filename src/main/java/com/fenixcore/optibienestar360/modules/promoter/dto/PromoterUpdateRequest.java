package com.fenixcore.optibienestar360.modules.promoter.dto;

import com.fenixcore.optibienestar360.core.validation.MinimumAge;
import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for {@code PUT /v1/admin/promoters/{uuid}}. PATCH semantics —
 * only non-null fields are applied.
 *
 * <p>Fields intentionally NOT editable post-create:</p>
 * <ul>
 *   <li>{@code userUuid} / {@code personUuid} — switching the human
 *       behind a promoter row would break commission attribution
 *       history (V26 commissions reference promoter_id, not user_id).
 *       Soft-delete + create new if reassignment is needed.</li>
 *   <li>{@code referralCode} — permanent attribution link per v2 PDF 2.a
 *       analogue. Renaming would invalidate flyers / WhatsApp shares
 *       already in circulation.</li>
 *   <li>{@code is_system} — set once at seed, immutable.</li>
 *   <li>{@code totalReferrals} / {@code totalCommissionPaid} — owned by
 *       the commission engine, not by admin edits.</li>
 * </ul>
 *
 * <p>Dossier fields (V180) follow the same PATCH semantics; identity fields
 * are written onto the linked {@code persons} row (shared identity hub).</p>
 */
public record PromoterUpdateRequest(
        @Size(max = 120) String displayName,
        String description,

        @Email @Size(max = 320) String email,
        @Size(max = 30) String phone,

        UUID promoterTypeUuid,

        Boolean active,
        @Pattern(regexp = "^(ACTIVE|INACTIVE|SUSPENDED)$",
                 message = "{promoter.status.allowed_values}")
        String status,

        // ─── Dossier: registration + executive profile (V180) ──────────────
        LocalDate joinedAt,
        @Size(max = 120) String assignedZone,
        @Pattern(regexp = "^(PRIMARY|SECONDARY|TECHNICAL|UNIVERSITY|POSTGRADUATE)$",
                 message = "{validation.education_level.allowed}")
        String educationLevel,
        @Size(max = 120) String profession,
        @PositiveOrZero @Max(80) Short yearsOfExperience,
        @Size(max = 150) String lastCompany,
        @Size(max = 100) String lastPosition,
        @Size(max = 120) String vehicle,

        // ─── Dossier: identity, applied to the linked person (V15/V29) ─────
        @Size(max = 50) String firstName,
        @Size(max = 50) String lastName,
        @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}")
        String documentType,
        @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
        @MinimumAge LocalDate birthDate,
        UUID maritalStatusUuid,
        @Size(max = 30) String landlinePhone,
        String address,
        UUID cityUuid
) {}
