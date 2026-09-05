package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Locks the v2 pricing rule: <b>the holder's monthly fee never changes because of
 * extra beneficiaries.</b> Whatever the plan's beneficiary configuration, the
 * membership snapshots exactly {@code plan.monthlyFee} — the charge for an extra
 * beneficiary is {@code extraBeneficiaryInscriptionFee}, a <i>one-time
 * inscription</i> fee, and must never leak into the recurring amount.
 *
 * <p>Today the rule holds by construction: {@code MembershipsService.enroll}
 * copies the fee straight from the plan, and {@code BeneficiariesService} has no
 * access to memberships or plans at all (its dependencies are the member and
 * beneficiary repositories plus the person service), so it <i>cannot</i> alter
 * pricing. These tests exist so a future change to the enrollment or beneficiary
 * path can't quietly start scaling the monthly fee.</p>
 */
@ExtendWith(MockitoExtension.class)
class MembershipPricingInvariantsTest {

    @Mock private MemberRepository memberRepository;
    @Mock private PlanRepository planRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MembershipMapper mapper;
    @Mock private com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher conversionEnricher;

    private MembershipsService service;

    @BeforeEach
    void setup() {
        service = new MembershipsService(memberRepository, planRepository, membershipRepository, mapper, conversionEnricher);
    }

    @Test
    void enroll_snapshotsExactlyThePlanMonthlyFee_notInflatedByTheExtraBeneficiaryFee() {
        // Familiar-style plan: allows paid extras on top of the included ones.
        Plan plan = plan(new BigDecimal("5.00"), new BigDecimal("10.00"), 3, 5, new BigDecimal("5.00"));

        Membership saved = enrollWith(plan);

        // The recurring amount is the plan's, untouched by the extra-beneficiary fee.
        assertThat(saved.getMonthlyFee()).isEqualByComparingTo("5.00");
        assertThat(saved.getMonthlyFee()).isEqualByComparingTo(plan.getMonthlyFee());
        // Inscription is likewise the plan's own — the per-extra charge is billed
        // separately against each beneficiary, never folded in here.
        assertThat(saved.getInscriptionFee()).isEqualByComparingTo(plan.getInscriptionFee());
    }

    @Test
    void enroll_monthlyFeeIsIndependentOfTheBeneficiaryConfiguration() {
        BigDecimal monthly = new BigDecimal("5.00");
        // Individual: no beneficiaries allowed at all.
        Plan individual = plan(monthly, new BigDecimal("10.00"), 0, 0, null);
        // Familiar: 3 included, up to 5, each extra costs an inscription fee.
        Plan familiar = plan(monthly, new BigDecimal("10.00"), 3, 5, new BigDecimal("5.00"));

        BigDecimal individualFee = enrollWith(individual).getMonthlyFee();
        BigDecimal familiarFee = enrollWith(familiar).getMonthlyFee();

        // Same monthly fee in the plan → same snapshot, regardless of how many
        // beneficiaries the plan includes or allows.
        assertThat(individualFee).isEqualByComparingTo(familiarFee);
        assertThat(familiarFee).isEqualByComparingTo(monthly);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Runs enroll() against the given plan and returns the Membership handed to save(). */
    private Membership enrollWith(Plan plan) {
        UUID memberUuid = UUID.randomUUID();
        Member member = new Member();
        member.setId(1L);
        member.setUuid(memberUuid);

        when(memberRepository.findByUuid(memberUuid)).thenReturn(Optional.of(member));
        when(membershipRepository.existsByMemberIdAndActiveTrue(1L)).thenReturn(false);
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        service.enroll(memberUuid, new MembershipCreateRequest(plan.getUuid(), null, null));

        ArgumentCaptor<Membership> captor = ArgumentCaptor.forClass(Membership.class);
        verify(membershipRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private Plan plan(BigDecimal monthlyFee, BigDecimal inscriptionFee,
                      int includedBeneficiaries, Integer maxBeneficiaries,
                      BigDecimal extraBeneficiaryInscriptionFee) {
        Plan p = new Plan();
        p.setId(10L);
        p.setUuid(UUID.randomUUID());
        p.setMonthlyFee(monthlyFee);
        p.setInscriptionFee(inscriptionFee);
        p.setIncludedBeneficiaries(includedBeneficiaries);
        p.setMaxBeneficiaries(maxBeneficiaries);
        p.setExtraBeneficiaryInscriptionFee(extraBeneficiaryInscriptionFee);
        return p;
    }
}
