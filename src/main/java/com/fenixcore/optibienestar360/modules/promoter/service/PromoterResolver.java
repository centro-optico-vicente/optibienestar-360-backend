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
 * <p><b>Rule</b> — promoter-table precedence, then INSTITUCION fallback:</p>
 * <ol>
 *   <li>If a non-blank {@code referralCode} resolves to an <i>active</i>
 *       promoter (case-insensitive, codes are stored UPPER), attribute to that
 *       promoter — this is the sales-network tracking link (PDF #4).</li>
 *   <li>Otherwise (no code, unknown code, or a code that is actually an
 *       affiliate-to-affiliate referral code, not a promoter's) fall back to the
 *       {@code INSTITUCION} system promoter, so the 100% of the attribution goes
 *       to central administration (PDF #5 default attribution). The
 *       affiliate-referral program (V27 / {@code ReferralService}) is a separate
 *       concern wired elsewhere.</li>
 * </ol>
 *
 * <p>{@code INSTITUCION} is seeded in V25 and is therefore always present in a
 * healthy deployment; the only way this returns {@code null} is a broken seed,
 * which is logged so it surfaces without blocking the enrollment.</p>
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
        Promoter institucion = promoterRepository.findByReferralCode(SYSTEM_PROMOTER_CODE).orElse(null);
        if (institucion == null) {
            log.error("PromoterResolver: INSTITUCION system promoter not found (V25 seed missing?) — "
                    + "member will be enrolled without an attribution link");
        }
        return institucion;
    }
}
