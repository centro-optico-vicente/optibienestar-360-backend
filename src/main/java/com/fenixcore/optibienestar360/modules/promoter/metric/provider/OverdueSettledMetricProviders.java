package com.fenixcore.optibienestar360.modules.promoter.metric.provider;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipChargeRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricEvent;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code OVERDUE_SETTLED_COUNT/AMOUNT} (hub plan competitive-commission-rules, Fase 5, S3):
 * "vencida saldada" — a {@link MembershipCharge#getStatus() COVERED} charge whose covering
 * payment landed, on the rule's own {@link AchievementDateBasis}, <em>after</em> {@link
 * MembershipCharge#getDueDate()}. Charge-shaped, not payment-shaped like the sales/collection/
 * advance family in {@link PaymentMetricProviders} — {@code COUNT} counts settled-late charges,
 * {@code AMOUNT} sums the charge's own {@code amount} (never the covering payment's, since one
 * advance payment can settle several months at once — see {@code MembershipCharge}'s own
 * Javadoc). Attribution is the covering payment's promoter, same convention {@code
 * COLLECTION_*}/S1 use.
 *
 * <p><b>H10 caveat:</b> {@code membership_charges} had zero {@code OVERDUE}/{@code COVERED} rows
 * in every environment checked as of the Fase 0 freeze — this metric's real-data shape is
 * unvalidated. Verify against production data before relying on it for a live rule (hub plan
 * §11 "Riesgos").</p>
 */
final class OverdueSettledMetricProviders {

    private OverdueSettledMetricProviders() {
    }

    abstract static class AbstractOverdueSettledMetricProvider implements CompetitiveMetricProvider {

        protected abstract MembershipChargeRepository chargeRepository();

        /** {@code true}: each settled-late charge counts as 1. {@code false}: its {@code amount} is the value. */
        protected abstract boolean isCountMetric();

        @Override
        public boolean supportsEvents() {
            return true;
        }

        @Override
        public List<MetricEvent> events(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
            Instant from = window.start().atStartOfDay(AppTimeZone.ZONE).toInstant();
            Instant to = window.end().plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();
            List<MembershipCharge> candidates = chargeRepository().findOverdueSettledCandidatesInWindow(
                    from, to, scope.includeSystemPromoters(), nullIfEmpty(scope.promoterTypeIds()), nullIfEmpty(scope.rankIds()));

            List<MetricEvent> events = new ArrayList<>();
            for (MembershipCharge charge : candidates) {
                Payment payment = charge.getCoveredByPayment();
                Instant achievedAt = basisInstant(payment, basis);
                if (achievedAt == null || achievedAt.isBefore(from) || !achievedAt.isBefore(to)) {
                    continue; // outside the window on the rule's ACTUAL basis column
                }
                LocalDate achievedDate = achievedAt.atZone(AppTimeZone.ZONE).toLocalDate();
                if (!achievedDate.isAfter(charge.getDueDate())) {
                    continue; // settled on or before its due date — not "vencida saldada" (S3)
                }
                BigDecimal value = isCountMetric() ? BigDecimal.ONE : charge.getAmount();
                events.add(new MetricEvent(payment.getPromoter().getId(), value, achievedAt));
            }
            return events;
        }

        @Override
        public List<Candidate> snapshot(PeriodStrategies.Window window, AchievementDateBasis basis, MetricScope scope) {
            Map<Long, List<MetricEvent>> byPromoter = new LinkedHashMap<>();
            for (MetricEvent event : events(window, basis, scope)) {
                byPromoter.computeIfAbsent(event.promoterId(), k -> new ArrayList<>()).add(event);
            }
            List<Candidate> result = new ArrayList<>();
            for (Map.Entry<Long, List<MetricEvent>> entry : byPromoter.entrySet()) {
                BigDecimal total = BigDecimal.ZERO;
                Instant lastAchievedAt = null;
                for (MetricEvent event : entry.getValue()) {
                    total = total.add(event.value());
                    if (lastAchievedAt == null || event.achievedAt().isAfter(lastAchievedAt)) {
                        lastAchievedAt = event.achievedAt();
                    }
                }
                result.add(new Candidate(entry.getKey(), total, lastAchievedAt, entry.getValue().size()));
            }
            return result;
        }

        private static Instant basisInstant(Payment payment, AchievementDateBasis basis) {
            return switch (basis) {
                case PAYMENT_DATE -> payment.getPaymentDate();
                case REGISTERED_AT -> payment.getReceivedAt();
                case APPROVED_AT -> payment.getReviewedAt() != null ? payment.getReviewedAt() : payment.getReceivedAt();
            };
        }

        private static Collection<Long> nullIfEmpty(Collection<Long> ids) {
            return ids == null || ids.isEmpty() ? null : ids;
        }
    }

    @Component
    @RequiredArgsConstructor
    static class OverdueSettledCountMetricProvider extends AbstractOverdueSettledMetricProvider {
        private final MembershipChargeRepository chargeRepository;

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.OVERDUE_SETTLED_COUNT;
        }

        @Override
        protected MembershipChargeRepository chargeRepository() {
            return chargeRepository;
        }

        @Override
        protected boolean isCountMetric() {
            return true;
        }
    }

    @Component
    @RequiredArgsConstructor
    static class OverdueSettledAmountMetricProvider extends AbstractOverdueSettledMetricProvider {
        private final MembershipChargeRepository chargeRepository;

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.OVERDUE_SETTLED_AMOUNT;
        }

        @Override
        protected MembershipChargeRepository chargeRepository() {
            return chargeRepository;
        }

        @Override
        protected boolean isCountMetric() {
            return false;
        }
    }
}
