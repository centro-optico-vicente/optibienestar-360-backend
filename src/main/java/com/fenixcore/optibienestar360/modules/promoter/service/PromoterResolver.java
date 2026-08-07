package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves which {@link Promoter} a newly-enrolled member is attributed to
 * (v2 PDF #4 / #5). Extracted as its own collaborator so the rule is unit-
 * testable in isolation and shared by any enrollment path
 * ({@code MembersService.create} today, taquilla / self-signup later).
 *
 * <p><b>Rule</b> — promoter-table lookup only, no forced default:</p>
 * <ol>
 *   <li>If a non-blank {@code referralCode} resolves to an <i>active</i>
 *       promoter (case-insensitive, codes are stored UPPER), attribute to that
 *       promoter — this is the sales-network tracking link (PDF #4).</li>
 *   <li>Otherwise (no code, unknown code, or a code that is actually an
 *       affiliate-to-affiliate referral code, not a promoter's) return
 *       {@code null} — the member is enrolled without a promoter. An admin can
 *       link one later via {@code POST /v1/admin/members/{uuid}/assign-promoter}
 *       (accepts either a promoter UUID or a referral code). Commission
 *       attribution still falls back to {@code INSTITUCION} at calculation time
 *       ({@code CommissionService.resolvePromoter}), so an unlinked member does
 *       not block commissions from being generated — this method only controls
 *       what shows as the member's <i>displayed</i> current promoter.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromoterResolver {

    /** Natural key of the V25-seeded system promoter (central administration). */
    public static final String SYSTEM_PROMOTER_CODE = "INSTITUCION";

    private final PromoterRepository promoterRepository;

    public Promoter resolveForEnrollment(String referralCode) {
        if (referralCode != null && !referralCode.isBlank()) {
            String normalized = referralCode.trim().toUpperCase();
            Promoter byCode = promoterRepository.findByReferralCode(normalized).orElse(null);
            if (byCode != null && byCode.isActive()) {
                return byCode;
            }
        }
        return null;
    }
}
