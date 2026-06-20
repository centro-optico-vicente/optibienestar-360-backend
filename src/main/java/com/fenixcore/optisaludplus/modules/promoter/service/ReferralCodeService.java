package com.fenixcore.optisaludplus.modules.promoter.service;

import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.member.repository.MemberRepository;
import com.fenixcore.optisaludplus.modules.promoter.dto.ReferralCodeIssueRequest;
import com.fenixcore.optisaludplus.modules.promoter.dto.ReferralCodeIssueResponse;
import com.fenixcore.optisaludplus.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Owns one operation: issue (or regenerate) the affiliate referral code
 * on a member. Singular-name pattern matching {@code ReferralService} +
 * {@code CommissionService} — one focused engine, no admin queue
 * (queries live on {@code Member} directly via the existing CRUD).
 *
 * <p><b>Two paths</b>:</p>
 * <ol>
 *   <li><b>Auto-generate</b> ({@code customCode} null/blank) — produces
 *       an 8-char UPPER alphanumeric tag from {@link #ALPHABET} (omits
 *       0/O/1/I/L for visual clarity on printed cards / flyers) and
 *       retries up to {@link #GENERATION_RETRIES} on cross-table
 *       collision. Failure-to-find raises a generic 422 so the admin
 *       can retry; v1 scale makes hitting the cap astronomically
 *       unlikely.</li>
 *   <li><b>Custom / vanity</b> ({@code customCode} supplied) — pre-checks
 *       cross-table uniqueness (both {@code promoters.referral_code}
 *       and {@code members.referral_code}); idempotent return when the
 *       custom code equals the member's current code.</li>
 * </ol>
 *
 * <p><b>Cross-table uniqueness</b> (V27 design): codes live in two
 * tables and a single namespace. The resolver gives precedence to the
 * promoter table on collision (sales-team flow wins). This issuer
 * enforces "no overlap at write time" so the precedence rule never has
 * to kick in for new codes — only legacy collisions could exercise it.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralCodeService {

    /**
     * Alphabet for auto-generation. Excludes 0/O/1/I/L to keep printed
     * codes (cards, flyers) unambiguous. 30 chars × 8 positions = ~6.5e11
     * candidates — collision probability is negligible at v1 scale.
     */
    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    /** Length of auto-generated codes. V27 spec says 6-8 typical; we pick the upper bound. */
    static final int GENERATED_LENGTH = 8;

    /**
     * Cap on retries when an auto-generated candidate collides. At v1
     * scale we never get close — the cap is a safety net, not a budget.
     */
    static final int GENERATION_RETRIES = 10;

    private final MemberRepository memberRepository;
    private final PromoterRepository promoterRepository;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ReferralCodeIssueResponse issue(ReferralCodeIssueRequest request) {
        Member member = memberRepository.findByUuid(request.memberUuid())
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));

        if (!member.isActive()) {
            throw new IllegalArgumentException("referral_code.member.inactive");
        }

        String previousCode = member.getReferralCode();
        String customCode = normalize(request.customCode());

        // Idempotent short-circuit: vanity code equals current code.
        if (customCode != null && customCode.equals(previousCode)) {
            return new ReferralCodeIssueResponse(
                    member.getUuid(),
                    member.getPerson() != null ? member.getPerson().getFullName() : null,
                    previousCode,
                    previousCode,
                    false,
                    Instant.now());
        }

        String newCode;
        boolean generated;
        if (customCode != null) {
            ensureAvailable(customCode, member);
            newCode = customCode;
            generated = false;
        } else {
            newCode = generateUnique();
            generated = true;
        }

        member.setReferralCode(newCode);
        log.info("Referral code issued: member={} previous={} new={} generated={}",
                member.getUuid(), previousCode, newCode, generated);

        return new ReferralCodeIssueResponse(
                member.getUuid(),
                member.getPerson() != null ? member.getPerson().getFullName() : null,
                previousCode,
                newCode,
                generated,
                Instant.now());
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static String normalize(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Cross-table availability check used before assigning a custom code.
     * Excludes the target member from the members.referral_code check so
     * a redundant request doesn't false-positive (caller short-circuits
     * the equal-code case above, but this stays defensive).
     */
    private void ensureAvailable(String code, Member targetMember) {
        if (promoterRepository.existsByReferralCode(code)) {
            throw new IllegalArgumentException("referral_code.duplicate.promoter");
        }
        boolean takenByOtherMember = memberRepository.findByReferralCode(code)
                .map(other -> !other.getId().equals(targetMember.getId()))
                .orElse(false);
        if (takenByOtherMember) {
            throw new IllegalArgumentException("referral_code.duplicate.member");
        }
    }

    /**
     * Generates a fresh code that doesn't collide with either table.
     * Auto-generation never reuses an existing string, so the target
     * member's own code is irrelevant — any hit on either repo is a
     * collision and we retry.
     */
    private String generateUnique() {
        for (int attempt = 0; attempt < GENERATION_RETRIES; attempt++) {
            String candidate = generateRaw();
            if (!promoterRepository.existsByReferralCode(candidate)
                    && memberRepository.findByReferralCode(candidate).isEmpty()) {
                return candidate;
            }
            log.debug("Referral code candidate {} collided on attempt {}", candidate, attempt + 1);
        }
        throw new IllegalArgumentException("referral_code.generation_exhausted");
    }

    private String generateRaw() {
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
