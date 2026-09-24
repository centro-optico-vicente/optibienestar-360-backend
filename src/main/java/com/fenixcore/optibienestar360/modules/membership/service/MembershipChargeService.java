package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.core.util.ScheduledJobParams;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge.ChargeStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipChargeRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Generates and settles {@link MembershipCharge} rows — the payment-driven
 * half of the membership billing cycle (the date-driven half is
 * {@link MembershipStatusService}).
 *
 * <ul>
 *   <li>{@link #generateUpcomingCharges(LocalDate)} — daily job driver, called
 *       by {@code MembershipChargeGenerationJobRunner}: creates the next
 *       PENDING charge for every live membership once it's within
 *       {@code daysBeforeDue} of its scheduled collection date.</li>
 *   <li>{@link #applyPayment(Payment)} — called from
 *       {@code PaymentsService.approve()}: marks every {@link MembershipCharge}
 *       the payment covers as COVERED, advances the membership's
 *       {@code lastPaidThrough}/{@code nextDueDate}, and immediately
 *       re-evaluates the membership's lifecycle status (a SUSPENDED
 *       membership can flip back to ACTIVE without waiting for the next
 *       sweep).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MembershipChargeService {

    private static final String CHARGE_GENERATION_JOB_CODE = "MEMBERSHIP_CHARGE_GENERATION";
    private static final int DEFAULT_DAYS_BEFORE_DUE = 3;

    private final MembershipChargeRepository chargeRepository;
    private final MembershipRepository membershipRepository;
    private final ScheduledJobRepository jobRepository;
    private final MembershipStatusService membershipStatusService;

    // ─── Generation ─────────────────────────────────────────────────────────

    /**
     * Idempotent: returns the existing PENDING/COVERED/... row for the period
     * when one already exists (V153 unique constraint on
     * {@code (membership_id, period_start)}), otherwise creates a new PENDING
     * charge priced off the membership's own snapshot ({@code monthlyFee}/
     * {@code currency}), due on the same billing-cutover-day projection the
     * collection-commission engine uses.
     */
    @Transactional
    public MembershipCharge ensureChargeForPeriod(Membership membership, LocalDate period) {
        LocalDate periodStart = period.withDayOfMonth(1);
        return chargeRepository.findByMembership_IdAndPeriodStart(membership.getId(), periodStart)
                .orElseGet(() -> createCharge(membership, periodStart));
    }

    private MembershipCharge createCharge(Membership membership, LocalDate periodStart) {
        MembershipCharge charge = new MembershipCharge();
        charge.setMembership(membership);
        charge.setPeriodStart(periodStart);
        charge.setPeriodEnd(periodStart.withDayOfMonth(periodStart.lengthOfMonth()));
        charge.setDueDate(scheduledDueDate(membership, periodStart));
        charge.setAmount(membership.getMonthlyFee());
        charge.setCurrency(membership.getCurrency());
        charge.setStatus(ChargeStatus.PENDING.name());
        return chargeRepository.save(charge);
    }

    /**
     * Batch driver for {@code MEMBERSHIP_CHARGE_GENERATION}: for every live
     * ACTIVE/SUSPENDED membership, works out the next chargeable period (the
     * month after {@code lastPaidThrough}, or the enrollment month when no
     * payment has ever been applied) and creates it once {@code today} is
     * within the configured {@code daysBeforeDue} window of that period's
     * due date.
     */
    @Transactional
    public BatchResult generateUpcomingCharges(LocalDate today) {
        int daysBeforeDue = readDaysBeforeDue();
        List<Membership> candidates = membershipRepository.findByActiveTrueAndStatusIn(
                List.of(LifecycleStatus.ACTIVE.name(), LifecycleStatus.SUSPENDED.name()));

        int created = 0;
        for (Membership membership : candidates) {
            LocalDate period = nextChargeablePeriod(membership);
            LocalDate dueDate = scheduledDueDate(membership, period);
            if (today.isBefore(dueDate.minusDays(daysBeforeDue))) {
                continue;
            }
            boolean existed = chargeRepository.existsByMembership_IdAndPeriodStart(
                    membership.getId(), period.withDayOfMonth(1));
            ensureChargeForPeriod(membership, period);
            if (!existed) {
                created++;
            }
        }

        log.info("MEMBERSHIP_CHARGE_GENERATION: scanned={} created={}", candidates.size(), created);
        return new BatchResult(candidates.size(), created);
    }

    private int readDaysBeforeDue() {
        return jobRepository.findByCode(CHARGE_GENERATION_JOB_CODE)
                .map(job -> ScheduledJobParams.intParam(job.getParameters(), "daysBeforeDue", DEFAULT_DAYS_BEFORE_DUE))
                .orElse(DEFAULT_DAYS_BEFORE_DUE);
    }

    private static LocalDate nextChargeablePeriod(Membership membership) {
        if (membership.getLastPaidThrough() == null) {
            return membership.getEnrolledAt().withDayOfMonth(1);
        }
        return membership.getLastPaidThrough().plusDays(1).withDayOfMonth(1);
    }

    // ─── Settlement ─────────────────────────────────────────────────────────

    /**
     * Applies an APPROVED, non-inscription collection payment to every
     * {@link MembershipCharge} it covers ({@code appliedPeriod} through
     * {@code coverageThroughPeriod}, inclusive — a single month when the
     * latter is {@code null}), advances the membership's
     * {@code lastPaidThrough}/{@code nextDueDate}, and re-evaluates its
     * lifecycle status immediately. No-op for inscription payments or
     * payments not tied to a membership (OUT commission payouts never reach
     * here — {@code PaymentsService.approve} only calls this inside the
     * {@code direction != OUT} branch).
     */
    @Transactional
    public void applyPayment(Payment payment) {
        if (payment.isInscription() || payment.getMembership() == null || payment.getAppliedPeriod() == null) {
            return;
        }
        Membership membership = payment.getMembership();
        LocalDate start = payment.getAppliedPeriod().withDayOfMonth(1);
        LocalDate end = (payment.getCoverageThroughPeriod() != null
                ? payment.getCoverageThroughPeriod() : payment.getAppliedPeriod()).withDayOfMonth(1);

        LocalDate lastCoveredMonthEnd = null;
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plusMonths(1)) {
            MembershipCharge charge = ensureChargeForPeriod(membership, cursor);
            if (!ChargeStatus.COVERED.name().equals(charge.getStatus())) {
                charge.setStatus(ChargeStatus.COVERED.name());
                charge.setCoveredByPayment(payment);
            }
            lastCoveredMonthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
        }

        if (lastCoveredMonthEnd != null
                && (membership.getLastPaidThrough() == null || lastCoveredMonthEnd.isAfter(membership.getLastPaidThrough()))) {
            membership.setLastPaidThrough(lastCoveredMonthEnd);
            LocalDate nextPeriod = lastCoveredMonthEnd.plusDays(1);
            membership.setNextDueDate(scheduledDueDate(membership, nextPeriod));
        }

        // Immediate re-evaluation so a SUSPENDED membership can flip back to
        // ACTIVE right away instead of waiting for the next daily sweep.
        membershipStatusService.applyTransition(membership, LocalDate.now());
    }

    /**
     * Same billing-cutover-day projection {@code CommissionService.collectionDays}
     * uses: {@link Membership#getBillingStartDay()} when set, else the
     * enrollment day-of-month, clamped to the target period's month length.
     * Duplicated (not extracted) to avoid touching the commission engine —
     * both copies must stay in sync if the projection rule ever changes.
     */
    private static LocalDate scheduledDueDate(Membership membership, LocalDate periodAnchor) {
        int day = membership.getBillingStartDay() != null
                ? membership.getBillingStartDay()
                : membership.getEnrolledAt().getDayOfMonth();
        YearMonth month = YearMonth.from(periodAnchor);
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    /** Summary returned by {@link #generateUpcomingCharges(LocalDate)}. */
    public record BatchResult(int scanned, int created) {}
}
