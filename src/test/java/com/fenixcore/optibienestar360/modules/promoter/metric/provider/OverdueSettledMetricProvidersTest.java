package com.fenixcore.optibienestar360.modules.promoter.metric.provider;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.membership.entity.MembershipCharge;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipChargeRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.metric.CompetitiveRankingEngine.Candidate;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricEvent;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import com.fenixcore.optibienestar360.modules.promoter.metric.provider.OverdueSettledMetricProviders.OverdueSettledAmountMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.provider.OverdueSettledMetricProviders.OverdueSettledCountMetricProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Fase 5 (hub plan competitive-commission-rules, S3 "vencida saldada"). Unit
 * coverage for the Java-side date/threshold logic since the real-data shape
 * is unvalidated (H10 — {@code membership_charges} has no OVERDUE/COVERED
 * rows in any environment checked so far).
 */
@ExtendWith(MockitoExtension.class)
class OverdueSettledMetricProvidersTest {

    @Mock private MembershipChargeRepository chargeRepository;

    private static final LocalDate WINDOW_START = LocalDate.of(2026, 6, 1);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 6, 30);
    private static final PeriodStrategies.Window WINDOW = new PeriodStrategies.Window(WINDOW_START, WINDOW_END);

    private void stubCandidates(MembershipCharge... charges) {
        when(chargeRepository.findOverdueSettledCandidatesInWindow(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(charges));
    }

    private static Promoter promoter(long id) {
        Promoter p = new Promoter();
        p.setId(id);
        return p;
    }

    private static Payment payment(long promoterId, LocalDate paymentDate) {
        Payment p = new Payment();
        p.setPromoter(promoter(promoterId));
        p.setPaymentDate(paymentDate.atStartOfDay(AppTimeZone.ZONE).toInstant());
        return p;
    }

    private static MembershipCharge charge(Payment coveringPayment, LocalDate dueDate, BigDecimal amount) {
        MembershipCharge mc = new MembershipCharge();
        mc.setCoveredByPayment(coveringPayment);
        mc.setDueDate(dueDate);
        mc.setAmount(amount);
        return mc;
    }

    @Test
    void countsOnlyChargesSettledAfterTheirDueDate() {
        Payment latePayment = payment(7L, LocalDate.of(2026, 6, 15));
        Payment onTimePayment = payment(8L, LocalDate.of(2026, 6, 10));
        stubCandidates(
                charge(latePayment, LocalDate.of(2026, 6, 5), new BigDecimal("50.00")),   // paid 10 days late
                charge(onTimePayment, LocalDate.of(2026, 6, 10), new BigDecimal("50.00"))); // paid exactly on due_date — not late

        List<MetricEvent> events = new OverdueSettledCountMetricProvider(chargeRepository)
                .events(WINDOW, AchievementDateBasis.PAYMENT_DATE, new MetricScope(null, null, false));

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().promoterId()).isEqualTo(7L);
        assertThat(events.getFirst().value()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void amountVariant_sumsTheChargesOwnAmount_notThePayments() {
        // One advance payment settles 2 months at once — each charge keeps its own amount.
        Payment advancePayment = payment(7L, LocalDate.of(2026, 6, 20));
        stubCandidates(
                charge(advancePayment, LocalDate.of(2026, 6, 1), new BigDecimal("30.00")),
                charge(advancePayment, LocalDate.of(2026, 6, 5), new BigDecimal("30.00")));

        List<MetricEvent> events = new OverdueSettledAmountMetricProvider(chargeRepository)
                .events(WINDOW, AchievementDateBasis.PAYMENT_DATE, new MetricScope(null, null, false));

        assertThat(events).hasSize(2);
        assertThat(events.stream().map(MetricEvent::value).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("60.00");
    }

    @Test
    void excludesCharges_settledOutsideTheWindow_onTheChosenBasis() {
        Payment lateButOutsideWindow = payment(7L, LocalDate.of(2026, 7, 3)); // after WINDOW_END
        stubCandidates(charge(lateButOutsideWindow, LocalDate.of(2026, 6, 5), new BigDecimal("50.00")));

        List<MetricEvent> events = new OverdueSettledCountMetricProvider(chargeRepository)
                .events(WINDOW, AchievementDateBasis.PAYMENT_DATE, new MetricScope(null, null, false));

        assertThat(events).isEmpty();
    }

    @Test
    void snapshot_aggregatesMultipleSettledChargesPerPromoter() {
        Payment p1 = payment(7L, LocalDate.of(2026, 6, 10));
        Payment p2 = payment(7L, LocalDate.of(2026, 6, 20));
        stubCandidates(
                charge(p1, LocalDate.of(2026, 6, 1), new BigDecimal("50.00")),
                charge(p2, LocalDate.of(2026, 6, 5), new BigDecimal("50.00")));

        List<Candidate> snapshot = new OverdueSettledAmountMetricProvider(chargeRepository)
                .snapshot(WINDOW, AchievementDateBasis.PAYMENT_DATE, new MetricScope(null, null, false));

        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.getFirst().promoterId()).isEqualTo(7L);
        assertThat(snapshot.getFirst().value()).isEqualByComparingTo("100.00");
        assertThat(snapshot.getFirst().transactionCount()).isEqualTo(2);
    }

    @Test
    void metric_returnsTheExpectedEnumValue() {
        assertThat(new OverdueSettledCountMetricProvider(chargeRepository).metric().name())
                .isEqualTo("OVERDUE_SETTLED_COUNT");
        assertThat(new OverdueSettledAmountMetricProvider(chargeRepository).metric().name())
                .isEqualTo("OVERDUE_SETTLED_AMOUNT");
    }
}
