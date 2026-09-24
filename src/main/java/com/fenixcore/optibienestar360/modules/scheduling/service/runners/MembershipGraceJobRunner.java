package com.fenixcore.optibienestar360.modules.scheduling.service.runners;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.ScheduledJobParams;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationChannelResolver.RecipientType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.scheduling.entity.ScheduledJob;
import com.fenixcore.optibienestar360.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optibienestar360.modules.scheduling.service.JobRunResult;
import com.fenixcore.optibienestar360.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily job (vertical-9): "vencidos en período de gracia". Finds SUSPENDED
 * memberships (past due, still inside their grace window) and enqueues a
 * {@code payment-overdue} nudge a few days before the grace period expires —
 * distinct from the one-shot {@code membership-suspended} notice the status
 * sweep already sent on the transition day. Seeded as {@code MEMBERSHIP_GRACE}
 * in {@code scheduled_jobs} (V40).
 *
 * <p>The grace length is a per-membership snapshot, so the nudge day is
 * computed per row: {@code next_due_date + max(1, gracePeriodDays - 3)} —
 * roughly three days before expiry, clamped so short grace windows still fire.
 * The SUSPENDED set is small (only rows currently in grace), so filtering in
 * memory is cheap.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipGraceJobRunner implements ScheduledJobRunner {

    public static final String CODE = "MEMBERSHIP_GRACE";

    private static final String TEMPLATE = "payment-overdue";
    private static final String SUBJECT_KEY = "email.payment.overdue.subject";
    private static final String PROMOTER_TEMPLATE = "collection-reminder-promoter";
    private static final String PROMOTER_SUBJECT_KEY = "email.collection.reminder.promoter.subject";
    private static final int DEFAULT_DAYS_BEFORE_GRACE_END = 3;
    private static final int DEFAULT_DAYS_BEFORE_ADVISOR_NOTIFY = 3;

    private final ScheduledJobRepository jobRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipReminderEnqueuer enqueuer;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    @Transactional
    public JobRunResult run() {
        ScheduledJob job = jobRepository.findByCode(CODE).orElse(null);
        LocalDate today = LocalDate.now(resolveZone(job));
        int daysBeforeGraceEnd = job != null
                ? ScheduledJobParams.intParam(job.getParameters(), "daysBeforeGraceEnd", DEFAULT_DAYS_BEFORE_GRACE_END)
                : DEFAULT_DAYS_BEFORE_GRACE_END;
        int daysBeforeAdvisorNotify = job != null
                ? ScheduledJobParams.intParam(job.getParameters(), "daysBeforeAdvisorNotify", DEFAULT_DAYS_BEFORE_ADVISOR_NOTIFY)
                : DEFAULT_DAYS_BEFORE_ADVISOR_NOTIFY;
        List<Membership> suspended = membershipRepository
                .findByActiveTrueAndStatus(LifecycleStatus.SUSPENDED.name());

        int notified = 0;
        int skipped = 0;
        for (Membership membership : suspended) {
            if (isNudgeDay(membership, today, daysBeforeGraceEnd)) {
                if (enqueuer.enqueue(membership, TEMPLATE, SUBJECT_KEY)) {
                    notified++;
                } else {
                    skipped++;
                }
            }
            if (isAdvisorNotifyDay(membership, today, daysBeforeAdvisorNotify)) {
                notifyPromoter(membership);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("suspendedScanned", suspended.size());
        summary.put("notified", notified);
        summary.put("skipped", skipped);
        log.info("MEMBERSHIP_GRACE: today={} suspendedScanned={} notified={} skipped={}",
                today, suspended.size(), notified, skipped);
        return JobRunResult.success(summary);
    }

    /** Best-effort — a member outside any promoter's downline (or with no promoter email) is simply skipped. */
    private void notifyPromoter(Membership membership) {
        Member member = membership.getMember();
        Promoter promoter = member != null ? member.getPromoter() : null;
        if (promoter == null) {
            return;
        }
        enqueuer.enqueueToRecipient(promoter.getEmail(), null, membership,
                PROMOTER_TEMPLATE, PROMOTER_SUBJECT_KEY, RecipientType.PROMOTER);
    }

    /** The per-membership member nudge day: ~{@code daysBeforeGraceEnd} days before grace expiry, clamped. */
    private static boolean isNudgeDay(Membership membership, LocalDate today, int daysBeforeGraceEnd) {
        int offset = Math.max(1, membership.getGracePeriodDays() - daysBeforeGraceEnd);
        LocalDate nudgeDay = membership.getNextDueDate().plusDays(offset);
        return today.isEqual(nudgeDay);
    }

    /** Same shape as {@link #isNudgeDay}, its own configurable offset for the promoter/advisor notice. */
    private static boolean isAdvisorNotifyDay(Membership membership, LocalDate today, int daysBeforeAdvisorNotify) {
        int offset = Math.max(1, membership.getGracePeriodDays() - daysBeforeAdvisorNotify);
        LocalDate notifyDay = membership.getNextDueDate().plusDays(offset);
        return today.isEqual(notifyDay);
    }

    private ZoneId resolveZone(ScheduledJob job) {
        if (job == null) {
            return AppTimeZone.ZONE;
        }
        try {
            return ZoneId.of(job.getTimezone());
        } catch (RuntimeException ex) {
            log.warn("Invalid timezone '{}' on {} — falling back to America/Caracas",
                    job.getTimezone(), CODE);
            return AppTimeZone.ZONE;
        }
    }
}
