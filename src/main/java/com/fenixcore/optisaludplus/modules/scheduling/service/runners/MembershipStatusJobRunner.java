package com.fenixcore.optisaludplus.modules.scheduling.service.runners;

import com.fenixcore.optisaludplus.common.service.EmailService;
import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optisaludplus.modules.membership.repository.MembershipRepository;
import com.fenixcore.optisaludplus.modules.membership.service.MembershipStatusService;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.scheduling.repository.ScheduledJobRepository;
import com.fenixcore.optisaludplus.modules.scheduling.service.JobRunResult;
import com.fenixcore.optisaludplus.modules.scheduling.service.ScheduledJobRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * First concrete runner of the scheduled-jobs framework — the daily
 * "membership status sweep". Resolved by code {@code MEMBERSHIP_STATUS_SWEEP}
 * (seeded as a row in {@code scheduled_jobs} by V22).
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Resolve {@code today} in the configured job timezone (read from the
 *       same {@code scheduled_jobs} row so admins can shift the zone
 *       without code changes).</li>
 *   <li>Load every {@code ACTIVE} / {@code SUSPENDED} membership whose
 *       {@code next_due_date} has crossed (uses the V21 partial composite
 *       index).</li>
 *   <li>Evaluate the target status with
 *       {@link MembershipStatusService#evaluate(Membership, LocalDate)} —
 *       pure function, no I/O.</li>
 *   <li>Apply the transition via
 *       {@link MembershipStatusService#applyTransition(Membership, LocalDate)}.</li>
 *   <li>Fire a localized email notification to the member (template
 *       {@code membership-suspended} or {@code membership-expired}). Failure
 *       to send is logged + captured in the summary but does not abort the
 *       sweep; the lifecycle transition has already been persisted.</li>
 * </ol>
 *
 * <p>Notification policy: only the transition event triggers an email —
 * subsequent sweeps that find the membership already at the target status
 * are no-ops (the inline check skips them) so members are not re-notified
 * day after day.</p>
 *
 * <p>The whole {@code run()} is annotated {@code @Transactional}: the
 * candidate set is held in a managed persistence context so
 * dirty-checking on the {@code Membership} mutations flushes once at
 * commit time, and the email triggers fire after the DB writes are queued
 * (the {@code EmailService} call is itself {@code @Async}, so the actual
 * send happens off-thread and does not extend the transaction).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipStatusJobRunner implements ScheduledJobRunner {

    public static final String CODE = "MEMBERSHIP_STATUS_SWEEP";

    private static final String TEMPLATE_SUSPENDED = "membership-suspended";
    private static final String TEMPLATE_EXPIRED   = "membership-expired";

    private static final String SUBJECT_KEY_SUSPENDED = "email.membership.suspended.subject";
    private static final String SUBJECT_KEY_EXPIRED   = "email.membership.expired.subject";

    private final ScheduledJobRepository jobRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipStatusService statusService;
    private final EmailService emailService;
    private final MessageSource messageSource;
    private final com.fenixcore.optisaludplus.modules.validator.service.ValidatorCacheService validatorCacheService;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    @Transactional
    public JobRunResult run() {
        LocalDate today = LocalDate.now(resolveZone());
        List<Membership> candidates = membershipRepository.findStatusEvaluationCandidates(today);

        int suspended = 0;
        int expired = 0;
        int skipped = 0;
        Map<String, String> notificationFailures = new LinkedHashMap<>();

        for (Membership membership : candidates) {
            LifecycleStatus target = statusService.evaluate(membership, today);
            if (target == null || target.name().equals(membership.getStatus())) {
                skipped++;
                continue;
            }

            statusService.applyTransition(membership, today);

            validatorCacheService.evictForMembership(membership);

            try {
                fireNotification(membership, target);
            } catch (RuntimeException ex) {
                log.error("Failed to dispatch transition email for membership {} (target {})",
                        membership.getUuid(), target, ex);
                notificationFailures.put(membership.getUuid().toString(), ex.getMessage());
            }

            if (target == LifecycleStatus.SUSPENDED) {
                suspended++;
            } else if (target == LifecycleStatus.EXPIRED) {
                expired++;
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("scanned", candidates.size());
        summary.put("suspended", suspended);
        summary.put("expired", expired);
        summary.put("skipped", skipped);
        if (!notificationFailures.isEmpty()) {
            summary.put("notificationFailures", notificationFailures);
        }
        log.info("MEMBERSHIP_STATUS_SWEEP completed: scanned={} suspended={} expired={} skipped={} notifFailures={}",
                candidates.size(), suspended, expired, skipped, notificationFailures.size());
        return JobRunResult.success(summary);
    }

    /**
     * Reads the configured timezone from the {@code scheduled_jobs} row.
     * Falls back to {@code America/Caracas} if the row is missing (e.g.
     * running this runner in isolation from unit tests without a seeded
     * job) — matches the project-wide default per ADR 0010 (localization
     * Venezuela).
     */
    private ZoneId resolveZone() {
        return jobRepository.findByCode(CODE)
                .map(job -> {
                    try {
                        return ZoneId.of(job.getTimezone());
                    } catch (RuntimeException ex) {
                        log.warn("Invalid timezone '{}' on {} job row — falling back to America/Caracas",
                                job.getTimezone(), CODE);
                        return ZoneId.of("America/Caracas");
                    }
                })
                .orElse(ZoneId.of("America/Caracas"));
    }

    private void fireNotification(Membership membership, LifecycleStatus target) {
        Person person = personFor(membership);
        if (person == null) {
            log.warn("Membership {} has no person attached — skipping notification", membership.getUuid());
            return;
        }
        String to = person.getEmail();
        if (to == null || to.isBlank()) {
            log.debug("Member {} has no email — skipping notification for membership {}",
                    person.getUuid(), membership.getUuid());
            return;
        }

        Locale locale = resolveLocale(person);
        String template = target == LifecycleStatus.SUSPENDED ? TEMPLATE_SUSPENDED : TEMPLATE_EXPIRED;
        String subjectKey = target == LifecycleStatus.SUSPENDED ? SUBJECT_KEY_SUSPENDED : SUBJECT_KEY_EXPIRED;
        String subject = messageSource.getMessage(subjectKey, null, locale);

        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", Optional.ofNullable(person.getFullName()).orElse(""));
        vars.put("planName", membership.getPlan().getName());
        vars.put("nextDueDate", membership.getNextDueDate());
        vars.put("gracePeriodDays", membership.getGracePeriodDays());

        emailService.sendTemplated(to, subject, template, locale, vars);
    }

    private static Person personFor(Membership membership) {
        Member member = membership.getMember();
        return member != null ? member.getPerson() : null;
    }

    private static Locale resolveLocale(Person person) {
        String tag = person.getLocale();
        if (tag == null || tag.isBlank()) return Locale.forLanguageTag("es");
        try {
            return Locale.forLanguageTag(tag);
        } catch (RuntimeException ex) {
            return Locale.forLanguageTag("es");
        }
    }
}
