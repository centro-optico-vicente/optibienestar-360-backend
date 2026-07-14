package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the plan-type → calculation table, the promoter resolution
 * (real → INSTITUCION fallback), and the skip paths (already exists,
 * unresolvable promoter, missing plan).
 */
@ExtendWith(MockitoExtension.class)
class CommissionServiceTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private PromoterRepository promoterRepository;

    @InjectMocks private CommissionService service;

    private Promoter humanPromoter;
    private Promoter institucion;

    @BeforeEach
    void setUp() {
        humanPromoter = promoter("PROMO123", false);
        institucion = promoter("INSTITUCION", true);
    }

    // ─── Plan-type calculation table ────────────────────────────────────────

    @Test
    void individual_inscription_yields_20_percent() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong()))
                .thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Optional<Commission> result = service.calculateAndPersistFor(payment);

        assertThat(result).isPresent();
        Commission c = result.get();
        assertThat(c.getAmount()).isEqualByComparingTo("2.00");  // 20% of $10
        assertThat(c.getCommissionPct()).isEqualByComparingTo("20.00");
        assertThat(c.getFlatAmount()).isNull();
        assertThat(c.getTierNameSnapshot()).isEqualTo("v1-individual-20pct");
        assertThat(c.getAppliesTo()).isEqualTo(AppliesTo.INSCRIPTION);
        assertThat(c.getPromoter()).isEqualTo(humanPromoter);
    }

    @Test
    void familiar_monthly_yields_25_percent() {
        Payment payment = paymentFor(PlanType.FAMILIAR, new BigDecimal("20.00"), false);
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong()))
                .thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("5.00");  // 25% of $20
        assertThat(c.getCommissionPct()).isEqualByComparingTo("25.00");
        assertThat(c.getFlatAmount()).isNull();
        assertThat(c.getTierNameSnapshot()).isEqualTo("v1-familiar-25pct");
        assertThat(c.getAppliesTo()).isEqualTo(AppliesTo.MONTHLY);
    }

    @Test
    void corporativo_yields_flat_5() {
        Payment payment = paymentFor(PlanType.CORPORATIVO, new BigDecimal("500.00"), false);
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong()))
                .thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getAmount()).isEqualByComparingTo("5.00");  // flat, regardless of basis
        assertThat(c.getCommissionPct()).isNull();
        assertThat(c.getFlatAmount()).isEqualByComparingTo("5.00");
        assertThat(c.getTierNameSnapshot()).isEqualTo("v1-corporativo-5flat");
    }

    // ─── Promoter resolution ────────────────────────────────────────────────

    @Test
    void member_without_promoter_falls_back_to_institucion() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        payment.getMembership().getMember().setPromoter(null);  // no direct promoter
        when(promoterRepository.findByReferralCode("INSTITUCION"))
                .thenReturn(Optional.of(institucion));
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong()))
                .thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getPromoter()).isEqualTo(institucion);
        assertThat(c.getAmount()).isEqualByComparingTo("2.00");
    }

    @Test
    void no_promoter_and_no_institucion_seed_skips_silently() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        payment.getMembership().getMember().setPromoter(null);
        when(promoterRepository.findByReferralCode("INSTITUCION"))
                .thenReturn(Optional.empty());

        Optional<Commission> result = service.calculateAndPersistFor(payment);

        assertThat(result).isEmpty();
        verify(commissionRepository, never()).save(any());
    }

    @Test
    void inactive_direct_promoter_falls_back_to_institucion() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        humanPromoter.setActive(false);  // direct promoter soft-deleted
        when(promoterRepository.findByReferralCode("INSTITUCION"))
                .thenReturn(Optional.of(institucion));
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong()))
                .thenReturn(false);
        when(commissionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Commission c = service.calculateAndPersistFor(payment).orElseThrow();

        assertThat(c.getPromoter()).isEqualTo(institucion);
    }

    // ─── Idempotency ────────────────────────────────────────────────────────

    @Test
    void already_existing_commission_skips_without_creating_another() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        when(commissionRepository.existsActiveForPaymentAndPromoter(anyLong(), anyLong()))
                .thenReturn(true);

        Optional<Commission> result = service.calculateAndPersistFor(payment);

        assertThat(result).isEmpty();
        verify(commissionRepository, never()).save(any());
    }

    // ─── Defensive paths ────────────────────────────────────────────────────

    @Test
    void null_payment_returns_empty() {
        assertThat(service.calculateAndPersistFor(null)).isEmpty();
    }

    @Test
    void payment_without_plan_type_skips() {
        Payment payment = paymentFor(PlanType.INDIVIDUAL, new BigDecimal("10.00"), true);
        payment.getMembership().getPlan().setType(null);  // type missing

        Optional<Commission> result = service.calculateAndPersistFor(payment);

        assertThat(result).isEmpty();
        verify(commissionRepository, never()).save(any());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

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
        payment.setCurrency("USD");
        payment.setInscription(inscription);
        payment.setPaymentDate(LocalDate.of(2026, 6, 15));
        if (!inscription) {
            payment.setAppliedPeriod(LocalDate.of(2026, 6, 1));
        }
        return payment;
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
