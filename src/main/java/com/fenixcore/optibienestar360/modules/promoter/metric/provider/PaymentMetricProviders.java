package com.fenixcore.optibienestar360.modules.promoter.metric.provider;

import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionRule.CompetitiveMetric;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * The 6 {@code Payment}-backed metric providers (hub plan competitive-commission-rules, Fase 2a):
 * sales (inscription payments), collection (recurring payments) and advance (multi-period
 * coverage payments), each in a {@code _COUNT} and {@code _AMOUNT} flavor. Grouped in one file —
 * each is a 2-line override of {@link AbstractPaymentMetricProvider}'s query + count-vs-amount
 * switch, not worth 6 separate files.
 */
final class PaymentMetricProviders {

    private PaymentMetricProviders() {
    }

    @Component
    @RequiredArgsConstructor
    static class SalesCountMetricProvider extends AbstractPaymentMetricProvider {
        private final PaymentRepository paymentRepository;

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.SALES_COUNT;
        }

        @Override
        protected boolean isCountMetric() {
            return true;
        }

        @Override
        protected List<Payment> fetchCandidates(Instant from, Instant to, boolean includeSystem,
                                                 Collection<Long> typeIds, Collection<Long> rankIds) {
            return paymentRepository.findSalesCandidatesInWindow(from, to, includeSystem, typeIds, rankIds);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class SalesAmountMetricProvider extends AbstractPaymentMetricProvider {
        private final PaymentRepository paymentRepository;

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.SALES_AMOUNT;
        }

        @Override
        protected boolean isCountMetric() {
            return false;
        }

        @Override
        protected List<Payment> fetchCandidates(Instant from, Instant to, boolean includeSystem,
                                                 Collection<Long> typeIds, Collection<Long> rankIds) {
            return paymentRepository.findSalesCandidatesInWindow(from, to, includeSystem, typeIds, rankIds);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class CollectionCountMetricProvider extends AbstractPaymentMetricProvider {
        private final PaymentRepository paymentRepository;

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.COLLECTION_COUNT;
        }

        @Override
        protected boolean isCountMetric() {
            return true;
        }

        @Override
        protected List<Payment> fetchCandidates(Instant from, Instant to, boolean includeSystem,
                                                 Collection<Long> typeIds, Collection<Long> rankIds) {
            return paymentRepository.findCollectionCandidatesInWindow(from, to, includeSystem, typeIds, rankIds);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class CollectionAmountMetricProvider extends AbstractPaymentMetricProvider {
        private final PaymentRepository paymentRepository;

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.COLLECTION_AMOUNT;
        }

        @Override
        protected boolean isCountMetric() {
            return false;
        }

        @Override
        protected List<Payment> fetchCandidates(Instant from, Instant to, boolean includeSystem,
                                                 Collection<Long> typeIds, Collection<Long> rankIds) {
            return paymentRepository.findCollectionCandidatesInWindow(from, to, includeSystem, typeIds, rankIds);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class AdvanceCountMetricProvider extends AbstractPaymentMetricProvider {
        private final PaymentRepository paymentRepository;

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.ADVANCE_COUNT;
        }

        @Override
        protected boolean isCountMetric() {
            return true;
        }

        @Override
        protected List<Payment> fetchCandidates(Instant from, Instant to, boolean includeSystem,
                                                 Collection<Long> typeIds, Collection<Long> rankIds) {
            return paymentRepository.findAdvanceCandidatesInWindow(from, to, includeSystem, typeIds, rankIds);
        }
    }

    @Component
    @RequiredArgsConstructor
    static class AdvanceAmountMetricProvider extends AbstractPaymentMetricProvider {
        private final PaymentRepository paymentRepository;

        @Override
        public CompetitiveMetric metric() {
            return CompetitiveMetric.ADVANCE_AMOUNT;
        }

        @Override
        protected boolean isCountMetric() {
            return false;
        }

        @Override
        protected List<Payment> fetchCandidates(Instant from, Instant to, boolean includeSystem,
                                                 Collection<Long> typeIds, Collection<Long> rankIds) {
            return paymentRepository.findAdvanceCandidatesInWindow(from, to, includeSystem, typeIds, rankIds);
        }
    }
}
