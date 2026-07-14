package com.fenixcore.optibienestar360.modules.promoter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
 */
public record PromoterUpdateRequest(
        @Size(max = 120) String displayName,
        String description,

        @Email @Size(max = 320) String email,
        @Size(max = 30) String phone,

        Boolean active,
        @Pattern(regexp = "^(ACTIVE|INACTIVE|SUSPENDED)$",
                 message = "{promoter.status.allowed_values}")
        String status
) {}
