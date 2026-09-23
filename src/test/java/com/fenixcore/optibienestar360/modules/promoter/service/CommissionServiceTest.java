package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.PeriodStrategy;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CollectionCommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tier-driven commission engine (V42). Base tiers (threshold 0) reproduce the v1
 * per-plan rates; a volume tier is applied when the promoter's new-subscriber
 * count meets its threshold. Also covers promoter resolution and skip paths.
 */
@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private CommissionTierRepository tierRepository;
    @Mock private CollectionCommissionTierRepository collectionTierRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CommissionAuditRecorder auditRecorder;
    @Mock private ConversionEnricher conversionEnricher;
    @Mock private com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService currencyConversionService;

    @InjectMocks private CommissionService service;

    private Promoter humanPromoter;
    private Promoter institucion;

    @BeforeEach
    void setUp() {
        humanPromoter = promoter("PROMO123", false);
        institucion = promoter("INSTITUCION", true);
        lenient().when(conversionEnricher.officialRateAt(any(), any())).thenReturn(ConversionEnricher.RateSnapshot.none());
    }

    // ─── Base tiers reproduce the v1 rates ────────────────────────────────────

    @Test
    void individual_inscription_appliesBaseTier_20_percent() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        stubExistsFalseAndSave();
        when(tierRepository.findActiveApplicable(eq(PlanType.INDIVIDUAL), any(), any(), any()))
                .thenReturn(List.of(baseTier(PlanType.INDIVIDUAL, "20.00", null, "Individual base 20%")));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("2.00");   // 20% of $10
        assertThat(c.getCommissionPct()).isEqualByComparingTo("20.00");
        assertThat(c.getFlatAmount()).isNull();
        assertThat(c.getTierNameSnapshot()).isEqualTo("Individual base 20%");
        assertThat(c.getAppliesTo()).isEqualTo(AppliesTo.INSCRIPTION);
        assertThat(c.getPromoter()).isEqualTo(humanPromoter);
    }

    @Test
    void familiar_monthly_appliesBaseTier_25_percent() {
        Payment payment = paymentFor(PlanType.FAMILIAR, new BigDecimal("20.00"), false);
        stubExistsFalseAndSave();
        when(tierRepository.findActiveApplicable(eq(PlanType.FAMILIAR), any(), any(), any()))
                .thenReturn(List.of(baseTier(PlanType.FAMILIAR, "25.00", null, "Familiar base 25%")));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("5.00");   // 25% of $20
        assertThat(c.getCommissionPct()).isEqualByComparingTo("25.00");
        assertThat(c.getAppliesTo()).isEqualTo(AppliesTo.MONTHLY);
    }

    @Test
    void corporativo_appliesBaseTier_flat_5() {
        Payment payment = paymentFor(PlanType.CORPORATIVO, new BigDecimal("500.00"), false);
        stubExistsFalseAndSave();
        when(tierRepository.findActiveApplicable(eq(PlanType.CORPORATIVO), any(), any(), any()))
                .thenReturn(List.of(baseTier(PlanType.CORPORATIVO, null, "5.00", "Corporativo base $5")));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("5.00");   // flat, regardless of basis
        assertThat(c.getCommissionPct()).isNull();
        assertThat(c.getFlatAmount()).isEqualByComparingTo("5.00");
    }

    // ─── Volume tier ──────────────────────────────────────────────────────────

    @Test
    void volumeTier_appliedWhenThresholdMet() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        stubExistsFalseAndSave();
        // Candidates highest-threshold first: a volume tier (10 → 30%) then the base (0 → 20%).
        CommissionTier volume = tier(PlanType.INDIVIDUAL, "30.00", null, 10, "Volume 10+ 30%");
        when(tierRepository.findActiveApplicable(eq(PlanType.INDIVIDUAL), any(), any(), any()))
                .thenReturn(List.of(volume, baseTier(PlanType.INDIVIDUAL, "20.00", null, "Individual base 20%")));
        when(memberRepository.countNewSubscribersForPromoter(eq(humanPromoter.getId()), any(), any()))
                .thenReturn(15L);   // ≥ 10 → qualifies for the volume tier

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("3.00");   // 30% of $10
        assertThat(c.getTierNameSnapshot()).isEqualTo("Volume 10+ 30%");
    }

    @Test
    void volumeTier_skippedWhenThresholdNotMet_fallsToBase() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        stubExistsFalseAndSave();
        CommissionTier volume = tier(PlanType.INDIVIDUAL, "30.00", null, 10, "Volume 10+ 30%");
        when(tierRepository.findActiveApplicable(eq(PlanType.INDIVIDUAL), any(), any(), any()))
                .thenReturn(List.of(volume, baseTier(PlanType.INDIVIDUAL, "20.00", null, "Individual base 20%")));
        when(memberRepository.countNewSubscribersForPromoter(eq(humanPromoter.getId()), any(), any()))
                .thenReturn(3L);    // < 10 → base tier wins

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("2.00");   // base 20%
        assertThat(c.getTierNameSnapshot()).isEqualTo("Individual base 20%");
    }

    @Test
    void noApplicableTier_skipsSilently() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong())).thenReturn(false);
        when(tierRepository.findActiveApplicable(eq(PlanType.INDIVIDUAL), any(), any(), any())).thenReturn(List.of());

        Optional<Commission> result = service.calculateAndPersistFor(payment);

        assertThat(result).isEmpty();
        verify(commissionRepository, never()).save(any());
    }

    // ─── Collection-commission engine (V44/V47/V48) ────────────────────────────

    @Test
    void monthly_prefersCollectionTier_overVolumeTier() {
        Payment payment = paymentFor(PlanType.FAMILIAR, new BigDecimal("20.00"), false);
        payment.getMembership().setBillingStartDay(1);
        payment.getMembership().setMonthlyFee(new BigDecimal("8.75"));
        stubExistsFalseAndSave();
        // scheduled = 2026-06-01 (billingStartDay=1, appliedPeriod=2026-06-01); paid 2026-06-15 → 14 days late.
        CollectionCommissionTier collectionTier = collectionTier(20, "30.00", "Cobranza hasta 20 días");
        when(collectionTierRepository.findActiveApplicable(eq(14), any())).thenReturn(List.of(collectionTier));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("2.63");   // 30% of $8.75 monthlyFee, not the $20 payment
        assertThat(c.getCommissionPct()).isEqualByComparingTo("30.00");
        assertThat(c.getCollectionDays()).isEqualTo(14);
        assertThat(c.getCollectionTierId()).isEqualTo(collectionTier.getId());
        assertThat(c.getTierNameSnapshot()).isEqualTo("Cobranza hasta 20 días");
        verify(tierRepository, never()).findActiveApplicable(any(), any(), any(), any());
    }

    @Test
    void monthly_fallsBackToVolumeTier_whenNoCollectionTierApplicable() {
        Payment payment = paymentFor(PlanType.FAMILIAR, new BigDecimal("20.00"), false);
        stubExistsFalseAndSave();
        when(collectionTierRepository.findActiveApplicable(anyInt(), any())).thenReturn(List.of());
        when(tierRepository.findActiveApplicable(eq(PlanType.FAMILIAR), any(), any(), any()))
                .thenReturn(List.of(baseTier(PlanType.FAMILIAR, "25.00", null, "Familiar base 25%")));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("5.00");   // 25% of $20 payment (volume-tier basis)
        assertThat(c.getCollectionDays()).isNull();
        assertThat(c.getCollectionTierId()).isNull();
    }

    @Test
    void inscription_neverConsultsCollectionTiers() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        stubExistsFalseAndSave();
        when(tierRepository.findActiveApplicable(eq(PlanType.INDIVIDUAL), any(), any(), any()))
                .thenReturn(List.of(baseTier(PlanType.INDIVIDUAL, "20.00", null, "Individual base 20%")));

        service.calculateAndPersistFor(payment);

        verify(collectionTierRepository, never()).findActiveApplicable(anyInt(), any());
    }

    // ─── Promoter-type scoping (V46) ────────────────────────────────────────────

    @Test
    void selectTier_passesPromotersTypeId_toRepository() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        PromoterType type = promoterType(7L);
        humanPromoter.setPromoterType(type);
        stubExistsFalseAndSave();
        when(tierRepository.findActiveApplicable(eq(PlanType.INDIVIDUAL), any(), any(), eq(7L)))
                .thenReturn(List.of(baseTier(PlanType.INDIVIDUAL, "40.00", null, "Type-7 base 40%")));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getTierNameSnapshot()).isEqualTo("Type-7 base 40%");
    }

    // ─── Promoter resolution ────────────────────────────────────────────────

    @Test
    void member_without_promoter_falls_back_to_institucion() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        payment.getMembership().getMember().setPromoter(null);
        when(promoterRepository.findByReferralCode("INSTITUCION")).thenReturn(Optional.of(institucion));
        stubExistsFalseAndSave();
        when(tierRepository.findActiveApplicable(eq(PlanType.INDIVIDUAL), any(), any(), any()))
                .thenReturn(List.of(baseTier(PlanType.INDIVIDUAL, "20.00", null, "Individual base 20%")));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getPromoter()).isEqualTo(institucion);
        assertThat(c.getAmount()).isEqualByComparingTo("2.00");
    }

    @Test
    void no_promoter_and_no_institucion_seed_skips_silently() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        payment.getMembership().getMember().setPromoter(null);
        when(promoterRepository.findByReferralCode("INSTITUCION")).thenReturn(Optional.empty());

        assertThat(service.calculateAndPersistFor(payment)).isEmpty();
        verify(commissionRepository, never()).save(any());
    }

    @Test
    void inactive_direct_promoter_falls_back_to_institucion() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        humanPromoter.setActive(false);
        when(promoterRepository.findByReferralCode("INSTITUCION")).thenReturn(Optional.of(institucion));
        stubExistsFalseAndSave();
        when(tierRepository.findActiveApplicable(eq(PlanType.INDIVIDUAL), any(), any(), any()))
                .thenReturn(List.of(baseTier(PlanType.INDIVIDUAL, "20.00", null, "Individual base 20%")));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getPromoter()).isEqualTo(institucion);
    }

    // ─── Idempotency + defensive paths ────────────────────────────────────────

    @Test
    void already_existing_commission_skips_without_creating_another() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong())).thenReturn(true);

        assertThat(service.calculateAndPersistFor(payment)).isEmpty();
        verify(commissionRepository, never()).save(any());
    }

    @Test
    void null_payment_returns_empty() {
        assertThat(service.calculateAndPersistFor(null)).isEmpty();
    }

    @Test
    void payment_without_plan_type_skips() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        payment.getMembership().getPlan().setType(null);
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong())).thenReturn(false);

        assertThat(service.calculateAndPersistFor(payment)).isEmpty();
        verify(commissionRepository, never()).save(any());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private void stubExistsFalseAndSave() {
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong())).thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static CommissionTier baseTier(PlanType planType, String pct, String flat, String name) {
        return tier(planType, pct, flat, 0, name);
    }

    private static CollectionCommissionTier collectionTier(int maxDays, String pct, String name) {
        CollectionCommissionTier t = new CollectionCommissionTier();
        t.setId((long) name.hashCode());
        t.setUuid(UUID.randomUUID());
        t.setName(name);
        t.setMaxDays(maxDays);
        t.setCommissionPct(new BigDecimal(pct));
        return t;
    }

    private static PromoterType promoterType(long id) {
        PromoterType t = new PromoterType();
        t.setId(id);
        t.setUuid(UUID.randomUUID());
        t.setCode("TYPE-" + id);
        t.setName("Type " + id);
        return t;
    }

    private static CommissionTier tier(PlanType planType, String pct, String flat, int threshold, String name) {
        CommissionTier t = new CommissionTier();
        t.setId((long) name.hashCode());
        t.setUuid(UUID.randomUUID());
        t.setName(name);
        t.setPlanType(planType);
        t.setThresholdCount(threshold);
        t.setCommissionPct(pct != null ? new BigDecimal(pct) : null);
        t.setFlatAmount(flat != null ? new BigDecimal(flat) : null);
        t.setAccrualPeriodStrategy(PeriodStrategy.MONTHLY);
        t.setAppliesTo(CommissionTier.AppliesTo.BOTH);
        return t;
    }

    private Payment paymentFor(PlanType planType, BigDecimal amount, boolean inscription) {
        Plan plan = new Plan();
        plan.setId(11L);
        plan.setUuid(UUID.randomUUID());
        plan.setCode(planType.name());
        plan.setType(planType);

        Member member = new Member();
        member.setId(22L);
        member.setUuid(UUID.randomUUID());
        member.setPromoter(humanPromoter);

        Membership membership = new Membership();
        membership.setId(33L);
        membership.setUuid(UUID.randomUUID());
        membership.setMember(member);
        membership.setPlan(plan);

        Payment payment = new Payment();
        payment.setId(44L);
        payment.setUuid(UUID.randomUUID());
        payment.setMembership(membership);
        payment.setAmount(amount);
        payment.setCurrency(usd());
        payment.setInscription(inscription);
        payment.setPaymentDate(LocalDate.of(2026, 6, 15)
			.atStartOfDay(AppTimeZone.ZONE)
			.toInstant())
		;
        if (!inscription) {
            payment.setAppliedPeriod(LocalDate.of(2026, 6, 1));
        }
        return payment;
    }

    private static Currency usd() {
        Currency c = new Currency();
        c.setCode("USD");
        c.setName("Dolar estadounidense");
        c.setSymbol("US$");
        c.setDecimalPlaces((short) 2);
        return c;
    }

    private static Promoter promoter(String code, boolean system) {
        Promoter p = new Promoter();
        p.setId(code.equals("INSTITUCION") ? 1L : 2L);
        p.setUuid(UUID.randomUUID());
        p.setReferralCode(code);
        p.setDisplayName(code);
        p.setSystem(system);
        p.setActive(true);
        return p;
    }
}
