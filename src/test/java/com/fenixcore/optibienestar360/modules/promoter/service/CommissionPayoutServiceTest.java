package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCategory;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCategoryRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPayoutResponse;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionRetroactiveTopUp;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride.OverrideStatus;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRetroactiveTopUpRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommissionPayoutService} (V107, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §4, PR6) — the
 * commercial-approval gate: only APPROVED commissions are payable, and a
 * hierarchy override is only payable once the commission that ultimately
 * funds it (walking the source chain to its root) is APPROVED.
 */
@ExtendWith(MockitoExtension.class)
class CommissionPayoutServiceTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private PromoterHierarchyOverrideRepository overrideRepository;
    @Mock private CommissionRetroactiveTopUpRepository topUpRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentCategoryRepository paymentCategoryRepository;
    @Mock private PaymentMethodRepository paymentMethodRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;
    @Mock private CommissionAuditRecorder auditRecorder;
    @Mock private ConversionEnricher conversionEnricher;

    private static final UUID ACTOR_UUID = UUID.randomUUID();

    private CommissionPayoutService service() {
        lenient().when(conversionEnricher.officialRateAt(any(), any())).thenReturn(ConversionEnricher.RateSnapshot.none());
        // Only consumed on a non-dry-run execute() — markPaid resolves the
        // actor once per call regardless of whether any promoter batch ends
        // up creating a payout Payment (see createPayoutPayment's early-out
        // for a promoter with no Person, e.g. the plain `promoter()` fixture
        // below).
        lenient().when(userRepository.findByUuid(ACTOR_UUID)).thenReturn(Optional.of(new User()));
        return new CommissionPayoutService(commissionRepository, overrideRepository, topUpRepository,
                paymentRepository, paymentCategoryRepository, paymentMethodRepository, currencyRepository, userRepository,
                emailService, messageSource, auditRecorder, conversionEnricher);
    }

    private static Currency usd() {
        Currency c = new Currency();
        c.setCode("USD");
        return c;
    }

    private static Promoter promoter(Long id, String name) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setDisplayName(name);
        p.setReferralCode(name.toUpperCase());
        return p;
    }

    private static Commission commission(Long id, Promoter promoter, String status) {
        Commission c = new Commission();
        c.setId(id);
        c.setUuid(UUID.randomUUID());
        c.setPromoter(promoter);
        c.setAppliesTo(AppliesTo.INSCRIPTION);
        c.setAmount(new BigDecimal("50.00"));
        c.setCalculationBasis(new BigDecimal("500.00"));
        c.setCurrency(usd());
        c.setStatus(status);
        return c;
    }

    private static CommissionPayoutRequest request() {
        return new CommissionPayoutRequest(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "BATCH-1", false, null);
    }

    @Test
    void payoutOnlyIncludesApprovedCommissions_pendingIsExcluded() {
        Promoter promoter = promoter(1L, "ase");
        Commission approved = commission(10L, promoter, CommissionStatus.APPROVED.name());

        when(commissionRepository.findApprovedForPeriod(request().periodStart(), request().periodEnd()))
                .thenReturn(List.of(approved));
        when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());
        when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());

        CommissionPayoutResponse response = service().execute(request(), ACTOR_UUID);

        assertThat(response.totalCommissions()).isEqualTo(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(approved.getStatus()).isEqualTo(CommissionStatus.PAID.name());
        assertThat(approved.getPayoutReference()).isEqualTo("BATCH-1");
    }

    @Test
    void overrideIsPayableWhenItsRootCommissionIsApproved() {
        Promoter ase = promoter(1L, "ase");
        Promoter sup = promoter(2L, "sup");
        Commission approvedRoot = commission(10L, ase, CommissionStatus.APPROVED.name());

        PromoterHierarchyOverride override = new PromoterHierarchyOverride();
        override.setUuid(UUID.randomUUID());
        override.setPromoter(sup);
        override.setSourceCommission(approvedRoot);
        override.setAmount(new BigDecimal("5.00"));
        override.setBasisAmount(new BigDecimal("50.00"));
        override.setCurrency(usd());
        override.setStatus(OverrideStatus.PENDING.name());

        when(commissionRepository.findApprovedForPeriod(any(), any())).thenReturn(List.of());
        when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of(override));
        when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());

        CommissionPayoutResponse response = service().execute(request(), ACTOR_UUID);

        assertThat(response.totalCommissions()).isEqualTo(1);
        assertThat(override.getStatus()).isEqualTo(OverrideStatus.PAID.name());
    }

    @Test
    void overrideIsExcludedWhenItsRootCommissionIsStillPending() {
        Promoter ase = promoter(1L, "ase");
        Promoter sup = promoter(2L, "sup");
        Commission stillPendingRoot = commission(10L, ase, CommissionStatus.PENDING.name());

        PromoterHierarchyOverride override = new PromoterHierarchyOverride();
        override.setUuid(UUID.randomUUID());
        override.setPromoter(sup);
        override.setSourceCommission(stillPendingRoot);
        override.setAmount(new BigDecimal("5.00"));
        override.setCurrency(usd());
        override.setStatus(OverrideStatus.PENDING.name());

        when(commissionRepository.findApprovedForPeriod(any(), any())).thenReturn(List.of());
        when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of(override));
        when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());

        CommissionPayoutResponse response = service().execute(request(), ACTOR_UUID);

        assertThat(response.totalCommissions()).isZero();
        assertThat(override.getStatus()).isEqualTo(OverrideStatus.PENDING.name()); // untouched
    }

    @Test
    void multiLevelOverrideResolvesRootThroughSourceOverrideChain() {
        Promoter ase = promoter(1L, "ase");
        Promoter sup = promoter(2L, "sup");
        Promoter coord = promoter(3L, "coord");
        Commission approvedRoot = commission(10L, ase, CommissionStatus.APPROVED.name());

        PromoterHierarchyOverride level2 = new PromoterHierarchyOverride();
        level2.setUuid(UUID.randomUUID());
        level2.setPromoter(sup);
        level2.setSourceCommission(approvedRoot);
        level2.setAmount(new BigDecimal("5.00"));
        level2.setCurrency(usd());
        level2.setStatus(OverrideStatus.PAID.name()); // already settled earlier

        PromoterHierarchyOverride level3 = new PromoterHierarchyOverride();
        level3.setUuid(UUID.randomUUID());
        level3.setPromoter(coord);
        level3.setSourceOverride(level2);
        level3.setAmount(new BigDecimal("1.00"));
        level3.setCurrency(usd());
        level3.setStatus(OverrideStatus.PENDING.name());

        when(commissionRepository.findApprovedForPeriod(any(), any())).thenReturn(List.of());
        when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of(level3));
        when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());

        CommissionPayoutResponse response = service().execute(request(), ACTOR_UUID);

        assertThat(response.totalCommissions()).isEqualTo(1);
        assertThat(level3.getStatus()).isEqualTo(OverrideStatus.PAID.name());
    }

    @Test
    void topUpsAreIncludedWithoutASeparateGate_theyOnlyExistFromAlreadyPaidRows() {
        Promoter promoter = promoter(1L, "ase");
        CommissionRetroactiveTopUp topUp = new CommissionRetroactiveTopUp();
        topUp.setUuid(UUID.randomUUID());
        topUp.setPromoter(promoter);
        topUp.setRetroAmount(new BigDecimal("3.00"));
        topUp.setBasisAmount(new BigDecimal("300.00"));
        topUp.setCurrency(usd());
        topUp.setStatus(CommissionRetroactiveTopUp.TopUpStatus.PENDING.name());
        topUp.setPeriodStart(LocalDate.of(2026, 6, 1));
        topUp.setPeriodEnd(LocalDate.of(2026, 6, 30));

        when(commissionRepository.findApprovedForPeriod(any(), any())).thenReturn(List.of());
        when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());
        when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of(topUp));

        CommissionPayoutResponse response = service().execute(request(), ACTOR_UUID);

        assertThat(response.totalCommissions()).isEqualTo(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("3.00");
        assertThat(topUp.getStatus()).isEqualTo(CommissionRetroactiveTopUp.TopUpStatus.PAID.name());
    }

    @Test
    void dryRunNeverMutatesAnyRow() {
        Promoter promoter = promoter(1L, "ase");
        Commission approved = commission(10L, promoter, CommissionStatus.APPROVED.name());

        lenient().when(commissionRepository.findApprovedForPeriod(any(), any())).thenReturn(List.of(approved));
        lenient().when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());
        lenient().when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());

        CommissionPayoutRequest dryRunRequest = new CommissionPayoutRequest(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "BATCH-1", true, null);
        service().execute(dryRunRequest, ACTOR_UUID);

        assertThat(approved.getStatus()).isEqualTo(CommissionStatus.APPROVED.name()); // untouched
        assertThat(approved.getPaidAt()).isNull();
    }

    @Test
    void payoutCreatesRealOutPaymentForPromoterWithPerson() {
        Promoter promoter = promoter(1L, "ase");
        promoter.setPerson(new Person());
        Commission approved = commission(10L, promoter, CommissionStatus.APPROVED.name());

        when(commissionRepository.findApprovedForPeriod(any(), any())).thenReturn(List.of(approved));
        when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());
        when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());
        when(paymentCategoryRepository.findByCode("COMMISSION_REGULAR")).thenReturn(Optional.of(paymentCategory()));
        when(paymentMethodRepository.findByCode("OTHER")).thenReturn(Optional.of(paymentMethod()));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().execute(request(), ACTOR_UUID);

        assertThat(approved.getPayoutPayment()).isNotNull();
        assertThat(approved.getPayoutPayment().getDirection()).isEqualTo("OUT");
        assertThat(approved.getPayoutPayment().getAmount()).isEqualByComparingTo("50.00");
        assertThat(approved.getPayoutPayment().getLines()).hasSize(1);
    }

    @Test
    void payoutSkipsPaymentCreation_whenPromoterHasNoPerson_systemPlaceholder() {
        // INSTITUCION system promoter (V25) — no Person, so no `payments.person_id`
        // to point at; the row still gets marked PAID via payoutReference alone.
        Promoter systemPromoter = promoter(1L, "institucion");
        Commission approved = commission(10L, systemPromoter, CommissionStatus.APPROVED.name());

        when(commissionRepository.findApprovedForPeriod(any(), any())).thenReturn(List.of(approved));
        when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());
        when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());

        service().execute(request(), ACTOR_UUID);

        assertThat(approved.getStatus()).isEqualTo(CommissionStatus.PAID.name());
        assertThat(approved.getPayoutPayment()).isNull();
        assertThat(approved.getPayoutReference()).isEqualTo("BATCH-1");
    }

    private static PaymentCategory paymentCategory() {
        PaymentCategory c = new PaymentCategory();
        c.setCode("COMMISSION_REGULAR");
        c.setDirection("OUT");
        return c;
    }

    private static PaymentMethod paymentMethod() {
        PaymentMethod m = new PaymentMethod();
        m.setCode("OTHER");
        return m;
    }
}
