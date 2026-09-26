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
 * Daily job (vertical-9): "vencimientos próximos". Finds ACTIVE memberships
 * whose {@code next_due_date} is exactly three days out and enqueues a
 * {@code payment-reminder} notification per titular. Seeded as
 * {@code MEMBERSHIP_DUE_SOON} in {@code scheduled_jobs} (V40).
 *
 * <p>Exact-day match (today + 3) so each membership is reminded once per cycle;
 * enqueue delegates to {@link MembershipReminderEnqueuer}, and the persistent
 * queue worker delivers asynchronously.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipDueSoonJobRunner implements ScheduledJobRunner {

    public static final String CODE = "MEMBERSHIP_DUE_SOON";

    private static final String TEMPLATE = "payment-reminder";
    private static final String SUBJECT_KEY = "email.payment.reminder.subject";
    private static final String PROMOTER_TEMPLATE = "collection-reminder-promoter";
    private static final String PROMOTER_SUBJECT_KEY = "email.collection.reminder.promoter.subject";
    private static final int DEFAULT_DAYS_BEFORE_DUE = 3;

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
        int daysBeforeDue = job != null
                ? ScheduledJobParams.intParam(job.getParameters(), "daysBeforeDue", DEFAULT_DAYS_BEFORE_DUE)
                : DEFAULT_DAYS_BEFORE_DUE;
        LocalDate dueDate = today.plusDays(daysBeforeDue);
        List<Membership> due = membershipRepository
                .findByActiveTrueAndStatusAndNextDueDate(LifecycleStatus.ACTIVE.name(), dueDate);

        int reminded = 0;
        int skipped = 0;
        for (Membership membership : due) {
            if (enqueuer.enqueue(membership, TEMPLATE, SUBJECT_KEY)) {
                reminded++;
            } else {
                skipped++;
            }
            notifyPromoter(membership);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("dueDate", dueDate.toString());
        summary.put("matched", due.size());
        summary.put("reminded", reminded);
        summary.put("skipped", skipped);
        log.info("MEMBERSHIP_DUE_SOON: dueDate={} matched={} reminded={} skipped={}",
                dueDate, due.size(), reminded, skipped);
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

    private ZoneId resolveZone(ScheduledJob job) {
        if (job == null) {
            return AppTimeZone.ZONE;
        }
        try {
            return ZoneId.of(job.getTimezone());
        } catch (RuntimeException ex) {
            log.warn("Invalid timezone '{}' on {} — falling back to {}",
                    job.getTimezone(), CODE, AppTimeZone.ZONE);
            return AppTimeZone.ZONE;
        }
    }
}
