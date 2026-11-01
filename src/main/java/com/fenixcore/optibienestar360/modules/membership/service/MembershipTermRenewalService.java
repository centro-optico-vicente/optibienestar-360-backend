package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipTermUpdateRequest;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Membership term (validity) engine, hub ADR 0034:
 * <ul>
 *   <li>{@link #snapshotTerm} — copies the plan's rule onto a new membership and computes the first expiry
 *       (with the advisor / admin overrides);</li>
 *   <li>{@link #renewDueTerms(LocalDate)} — daily {@code MEMBERSHIP_TERM_RENEWAL}: renews the same row when
 *       {@code expires_at <= today}; with re-inscription it sets {@code inscription_due_since} and SUSPENDS;</li>
 *   <li>{@link #onInscriptionPaymentApproved(Payment)} — an approved inscription payment clears the pending
 *       re-inscription and re-evaluates the status;</li>
 *   <li>{@link #updateTerm} — admin edit of the expiry / re-inscription flag.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MembershipTermRenewalService {

    static final String REASON_RENEWED_REINSCRIPTION = "Term renewed - re-inscription pending";
    static final String REASON_REINSCRIPTION_PAID = "Re-inscription paid";

    private final MembershipRepository repository;
    private final MembershipStatusService statusService;
    private final BeneficiaryRepository beneficiaryRepository;
    private final MembershipMapper mapper;
    private final com.fenixcore.optibienestar360.modules.promotion.service.PromotionLifecycle promotionLifecycle;

    // ─── Snapshot at creation ───────────────────────────────────────────────

    /**
     * Snapshots the plan's term rule onto {@code membership} and sets {@code term_started_at} / {@code expires_at}.
     *
     * <p>Decisions (hub ADR 0034): the re-inscription override wins over the plan. Without re-inscription the
     * membership is open-ended, but an explicit {@code expiresAt} is still stored as a plain end date. With
     * re-inscription forced on a plan that has no rule, an explicit {@code expiresAt} is required and the term
     * renews every {@value MembershipTermRule#DEFAULT_PERIOD_MONTHS} months (PERIOD).</p>
     */
    static void snapshotTerm(Membership membership, Plan plan, LocalDate enrolledAt,
                             LocalDate expiresAtOverride, Boolean reinscriptionOverride) {
        if (expiresAtOverride != null && !expiresAtOverride.isAfter(enrolledAt)) {
            throw new IllegalArgumentException("membership.term.expires_before_enrolled");
        }
        boolean reinscription = reinscriptionOverride != null ? reinscriptionOverride : plan.isReinscriptionRequired();
        if (!reinscription) {
            clearRule(membership);
            membership.setTermStartedAt(null);
            membership.setExpiresAt(expiresAtOverride);
            return;
        }
        MembershipTermRule rule = MembershipTermRule.of(plan);
        if (rule == null) {
            if (expiresAtOverride == null) {
                throw new IllegalArgumentException("membership.term.expires_required");
            }
            rule = MembershipTermRule.period(MembershipTermRule.DEFAULT_PERIOD_MONTHS);
        }
        applyRule(membership, rule);
        membership.setTermStartedAt(enrolledAt);
        membership.setExpiresAt(expiresAtOverride != null ? expiresAtOverride : rule.firstExpiry(enrolledAt));
    }

    private static void applyRule(Membership membership, MembershipTermRule rule) {
        membership.setReinscriptionRequired(true);
        membership.setTermMode(rule.mode());
        membership.setTermFixedMonth(rule.fixedMonth());
        membership.setTermFixedDay(rule.fixedDay());
        membership.setTermPeriodMonths(rule.periodMonths());
    }

    private static void clearRule(Membership membership) {
        membership.setReinscriptionRequired(false);
        membership.setTermMode(null);
        membership.setTermFixedMonth(null);
        membership.setTermFixedDay(null);
        membership.setTermPeriodMonths(null);
        membership.setInscriptionDueSince(null);
    }

    // ─── Daily renewal ──────────────────────────────────────────────────────

    /**
     * Renews every live re-inscription membership whose term ended ({@code expires_at <= today}). Idempotent: a
     * renewed row has {@code expires_at > today}, so a second run finds nothing. Several missed terms (job down)
     * are rolled forward in one pass; the re-inscription date stays the first missed expiry.
     */
    @Transactional
    public BatchResult renewDueTerms(LocalDate today) {
        List<Membership> due = repository.findTermRenewalCandidates(today);
        int renewed = 0;
        int suspended = 0;
        for (Membership membership : due) {
            MembershipTermRule rule = MembershipTermRule.of(membership);
            if (rule == null || membership.getExpiresAt() == null) continue;

            LocalDate firstExpiry = membership.getExpiresAt();
            LocalDate start = firstExpiry;
            LocalDate end = rule.nextExpiry(start);
            while (!end.isAfter(today)) {
                start = end;
                end = rule.nextExpiry(start);
            }
            membership.setTermStartedAt(start);
            membership.setExpiresAt(end);
            renewed++;

            if (membership.getInscriptionDueSince() == null) {
                membership.setInscriptionDueSince(firstExpiry);
            }
            if (suspendForReinscription(membership)) suspended++;
        }
        log.info("MEMBERSHIP_TERM_RENEWAL: scanned={} renewed={} suspended={}", due.size(), renewed, suspended);
        return new BatchResult(due.size(), renewed, suspended);
    }

    private boolean suspendForReinscription(Membership membership) {
        if (LifecycleStatus.SUSPENDED.name().equals(membership.getStatus())) {
            return false;
        }
        membership.setStatus(LifecycleStatus.SUSPENDED.name());
        membership.setLastStatusChangeAt(Instant.now());
        membership.setLastStatusChangeReason(REASON_RENEWED_REINSCRIPTION);
        promotionLifecycle.onStatusChanged(membership, LifecycleStatus.SUSPENDED);
        return true;
    }

    // ─── Inscription payment ────────────────────────────────────────────────

    /**
     * An approved inscription payment applied to a membership with a pending re-inscription clears
     * {@code inscription_due_since} and re-evaluates the status. Extra-beneficiary inscriptions (also
     * {@code inscription} payments) are ignored. No-op otherwise. Returns whether the flag was cleared.
     */
    @Transactional
    public boolean onInscriptionPaymentApproved(Payment payment) {
        Membership membership = payment.getMembership();
        if (!payment.isInscription() || membership == null || membership.getInscriptionDueSince() == null) {
            return false;
        }
        if (payment.getId() != null && beneficiaryRepository.existsByInscriptionPaymentId(payment.getId())) {
            return false;
        }
        membership.setInscriptionDueSince(null);
        reevaluate(membership, REASON_REINSCRIPTION_PAID);
        return true;
    }

    /** Re-evaluates the date-driven status right away; when it stays unchanged nothing is written. */
    private void reevaluate(Membership membership, String reasonIfChanged) {
        if (statusService.applyTransition(membership, LocalDate.now())) {
            membership.setLastStatusChangeReason(reasonIfChanged);
        }
    }

    // ─── Admin edit ─────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "membership", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public MembershipDto updateTerm(UUID uuid, MembershipTermUpdateRequest req) {
        Membership membership = repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));
        if (!membership.isActive() || LifecycleStatus.CANCELED.name().equals(membership.getStatus())) {
            throw new IllegalArgumentException("membership.term.not_editable");
        }
        boolean wasPending = membership.getInscriptionDueSince() != null;
        boolean target = req.reinscriptionRequired() != null ? req.reinscriptionRequired() : membership.isReinscriptionRequired();

        if (!target) {
            LocalDate expiresAt = req.expiresAt();
            if (expiresAt != null && !expiresAt.isAfter(membership.getEnrolledAt())) {
                throw new IllegalArgumentException("membership.term.expires_before_enrolled");
            }
            clearRule(membership);
            membership.setTermStartedAt(null);
            membership.setExpiresAt(expiresAt);
            if (wasPending) reevaluate(membership, "Re-inscription no longer required");
            return mapper.toDto(membership);
        }

        MembershipTermRule rule = MembershipTermRule.of(membership);
        if (rule == null) {
            rule = MembershipTermRule.of(membership.getPlan());
        }
        LocalDate expiresAt = req.expiresAt() != null ? req.expiresAt() : membership.getExpiresAt();
        if (rule == null) {
            if (expiresAt == null) throw new IllegalArgumentException("membership.term.expires_required");
            rule = MembershipTermRule.period(MembershipTermRule.DEFAULT_PERIOD_MONTHS);
        }
        LocalDate termStart = membership.getTermStartedAt() != null ? membership.getTermStartedAt() : membership.getEnrolledAt();
        if (expiresAt == null) expiresAt = rule.firstExpiry(termStart);
        if (!expiresAt.isAfter(termStart)) {
            throw new IllegalArgumentException("membership.term.expires_before_enrolled");
        }
        applyRule(membership, rule);
        membership.setTermStartedAt(termStart);
        membership.setExpiresAt(expiresAt);
        return mapper.toDto(membership);
    }

    /** Summary returned by {@link #renewDueTerms(LocalDate)}. */
    public record BatchResult(int scanned, int renewed, int suspended) {}
}
