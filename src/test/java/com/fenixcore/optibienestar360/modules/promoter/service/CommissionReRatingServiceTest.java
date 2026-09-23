package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionReRatingResponse.PromoterReRatingOutcome;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommissionReRatingService} — month-close retroactive
 * re-rating (vertical-8 Ítem A). Locks: PENDING INSCRIPTION commissions bump to
 * the promoter's highest-reached monthly band; PAID rows and MONTHLY-applied
 * rows are never touched; re-runs are idempotent (no-op once at target band).
 */
@ExtendWith(MockitoExtension.class)
class CommissionReRatingServiceTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private CommissionTierRepository tierRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CommissionAuditRecorder auditRecorder;

    @InjectMocks private CommissionReRatingService service;

    private static final LocalDate JUN_1 = LocalDate.of(2026, 6, 1);
    private static final LocalDate JUN_30 = LocalDate.of(2026, 6, 30);

    @Test
    void bumpsAllPendingInscriptions_toHighestReachedBand() {
        Promoter promoter = promoter(7L);
        Commission c1 = pendingInscription(promoter, "10.00", "2.50", 1L);   // priced at 25% band (tier id 1)
        Commission c2 = pendingInscription(promoter, "10.00", "2.50", 1L);
        when(commissionRepository.findPendingForPeriod(JUN_1, JUN_30)).thenReturn(List.of(c1, c2));
        when(memberRepository.countNewSubscribersForPromoter(7L, JUN_1, JUN_30)).thenReturn(60L);
        CommissionTier band30 = tier(2L, 41, "30.00", "Inscripción 41-60/mes — 30%");
        CommissionTier band25 = tier(1L, 0, "25.00", "Inscripción 0-40/mes — 25%");
        when(tierRepository.findActiveApplicable(isNull(), eq(CommissionTier.AppliesTo.INSCRIPTION), any(), any()))
                .thenReturn(List.of(band30, band25));   // highest-threshold-first, as the repo orders

        CommissionReRatingResponse res = service.execute(new CommissionReRatingRequest(JUN_1, JUN_30, false));

        assertThat(c1.getCommissionPct()).isEqualByComparingTo("30.00");
        assertThat(c1.getAmount()).isEqualByComparingTo("3.00");   // 30% of $10
        assertThat(c1.getCommissionTierId()).isEqualTo(2L);
        assertThat(c2.getCommissionPct()).isEqualByComparingTo("30.00");
        assertThat(res.commissionsUpdated()).isEqualTo(2);
        assertThat(res.totalDeltaAmount()).isEqualByComparingTo("1.00");   // 2 × ($3.00 - $2.50)
        PromoterReRatingOutcome outcome = res.perPromoter().get(0);
        assertThat(outcome.inscriptionCount()).isEqualTo(60);
        assertThat(outcome.targetTierName()).isEqualTo("Inscripción 41-60/mes — 30%");
    }

    @Test
    void dryRun_computesDeltas_withoutMutatingCommissions() {
        Promoter promoter = promoter(7L);
        Commission c1 = pendingInscription(promoter, "10.00", "2.50", 1L);
        when(commissionRepository.findPendingForPeriod(JUN_1, JUN_30)).thenReturn(List.of(c1));
        when(memberRepository.countNewSubscribersForPromoter(7L, JUN_1, JUN_30)).thenReturn(60L);
        CommissionTier band30 = tier(2L, 41, "30.00", "Inscripción 41-60/mes — 30%");
        when(tierRepository.findActiveApplicable(isNull(), any(), any(), any())).thenReturn(List.of(band30));

        CommissionReRatingResponse res = service.execute(new CommissionReRatingRequest(JUN_1, JUN_30, true));

        assertThat(res.dryRun()).isTrue();
        assertThat(res.commissionsUpdated()).isEqualTo(1);
        assertThat(res.totalDeltaAmount()).isEqualByComparingTo("0.50");
        // Entity untouched — the whole point of a dry run.
        assertThat(c1.getCommissionPct()).isEqualByComparingTo("25.00");
        assertThat(c1.getAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void alreadyAtTargetBand_isNoOp() {
        Promoter promoter = promoter(7L);
        Commission c1 = pendingInscription(promoter, "10.00", "2.50", 1L);
        when(commissionRepository.findPendingForPeriod(JUN_1, JUN_30)).thenReturn(List.of(c1));
        when(memberRepository.countNewSubscribersForPromoter(7L, JUN_1, JUN_30)).thenReturn(10L);
        CommissionTier band25 = tier(1L, 0, "25.00", "Inscripción 0-40/mes — 25%");
        when(tierRepository.findActiveApplicable(isNull(), any(), any(), any())).thenReturn(List.of(band25));

        CommissionReRatingResponse res = service.execute(new CommissionReRatingRequest(JUN_1, JUN_30, false));

        assertThat(res.commissionsUpdated()).isZero();
        assertThat(res.totalDeltaAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void ignoresMonthlyCommissions_evenWhenPending() {
        Promoter promoter = promoter(7L);
        Commission monthly = pendingInscription(promoter, "8.75", "2.19", 9L);
        monthly.setAppliesTo(AppliesTo.MONTHLY);
        when(commissionRepository.findPendingForPeriod(JUN_1, JUN_30)).thenReturn(List.of(monthly));

        CommissionReRatingResponse res = service.execute(new CommissionReRatingRequest(JUN_1, JUN_30, false));

        assertThat(res.totalPromoters()).isZero();
        assertThat(res.commissionsUpdated()).isZero();
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static Promoter promoter(long id) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setReferralCode("P" + id);
        p.setDisplayName("Promoter " + id);
        p.setActive(true);
        return p;
    }

    private static Commission pendingInscription(Promoter promoter, String basis, String amount, Long tierId) {
        Commission c = new Commission();
        c.setUuid(UUID.randomUUID());
        c.setPromoter(promoter);
        c.setAppliesTo(AppliesTo.INSCRIPTION);
        c.setStatus("PENDING");
        c.setCalculationBasis(new BigDecimal(basis));
        c.setAmount(new BigDecimal(amount));
        c.setCommissionPct(new BigDecimal("25.00"));
        c.setCommissionTierId(tierId);
        c.setPeriodStrategy(PeriodStrategy.MONTHLY);
        c.setPeriodStart(JUN_1);
        c.setPeriodEnd(JUN_30);
        return c;
    }

    private static CommissionTier tier(long id, int threshold, String pct, String name) {
        CommissionTier t = new CommissionTier();
        t.setId(id);
        t.setUuid(UUID.randomUUID());
        t.setName(name);
        t.setPlanType(null);
        t.setThresholdCount(threshold);
        t.setCommissionPct(new BigDecimal(pct));
        t.setAccrualPeriodStrategy(PeriodStrategy.MONTHLY);
        t.setAppliesTo(CommissionTier.AppliesTo.INSCRIPTION);
        return t;
    }
}
