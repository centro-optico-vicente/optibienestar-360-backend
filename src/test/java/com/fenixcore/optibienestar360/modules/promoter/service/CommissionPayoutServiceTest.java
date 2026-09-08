package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
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
    @Mock private EmailService emailService;
    @Mock private MessageSource messageSource;
    @Mock private CommissionAuditRecorder auditRecorder;

    private CommissionPayoutService service() {
        return new CommissionPayoutService(commissionRepository, overrideRepository, topUpRepository,
                emailService, messageSource, auditRecorder);
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
        return new CommissionPayoutRequest(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "BATCH-1", false);
    }

    @Test
    void payoutOnlyIncludesApprovedCommissions_pendingIsExcluded() {
        Promoter promoter = promoter(1L, "ase");
        Commission approved = commission(10L, promoter, CommissionStatus.APPROVED.name());

        when(commissionRepository.findApprovedForPeriod(request().periodStart(), request().periodEnd()))
                .thenReturn(List.of(approved));
        when(overrideRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());
        when(topUpRepository.findPendingForPeriod(any(), any())).thenReturn(List.of());

        CommissionPayoutResponse response = service().execute(request());

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

        CommissionPayoutResponse response = service().execute(request());

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

        CommissionPayoutResponse response = service().execute(request());

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

        CommissionPayoutResponse response = service().execute(request());

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

        CommissionPayoutResponse response = service().execute(request());

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
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), "BATCH-1", true);
        service().execute(dryRunRequest);

        assertThat(approved.getStatus()).isEqualTo(CommissionStatus.APPROVED.name()); // untouched
        assertThat(approved.getPaidAt()).isNull();
    }
}
