package com.fenixcore.optibienestar360.modules.promoter.metric.provider;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import com.fenixcore.optibienestar360.core.util.PeriodStrategies;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.AchievementDateBasis;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricEvent;
import com.fenixcore.optibienestar360.modules.promoter.metric.MetricScope;
import com.fenixcore.optibienestar360.modules.promoter.metric.provider.PaymentMetricProviders.SalesAmountMetricProvider;
import com.fenixcore.optibienestar360.modules.promoter.metric.provider.PaymentMetricProviders.SalesCountMetricProvider;
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
 * Amount metrics count what was actually collected (ADR 0017): a one-off
 * discount lowers the event value; count metrics are unaffected.
 */
@ExtendWith(MockitoExtension.class)
class PaymentMetricProvidersTest {

    @Mock private PaymentRepository paymentRepository;

    private static final PeriodStrategies.Window WINDOW =
            new PeriodStrategies.Window(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
    private static final MetricScope SCOPE = new MetricScope(null, null, false);

    private void stubDiscountedSale() {
        Promoter promoter = new Promoter();
        promoter.setId(7L);
        Payment payment = new Payment();
        payment.setPromoter(promoter);
        payment.setAmount(new BigDecimal("200.00"));
        payment.setDiscountAmount(new BigDecimal("50.00"));
        payment.setPaymentDate(LocalDate.of(2026, 6, 10).atStartOfDay(AppTimeZone.ZONE).toInstant());
        when(paymentRepository.findSalesCandidatesInWindow(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(payment));
    }

    @Test
    void amountMetric_usesNetAmount() {
        stubDiscountedSale();

        List<MetricEvent> events = new SalesAmountMetricProvider(paymentRepository)
                .events(WINDOW, AchievementDateBasis.PAYMENT_DATE, SCOPE);

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().value()).isEqualByComparingTo("150.00");
    }

    @Test
    void countMetric_isUnaffectedByDiscount() {
        stubDiscountedSale();

        List<MetricEvent> events = new SalesCountMetricProvider(paymentRepository)
                .events(WINDOW, AchievementDateBasis.PAYMENT_DATE, SCOPE);

        assertThat(events.getFirst().value()).isEqualByComparingTo(BigDecimal.ONE);
    }
}
