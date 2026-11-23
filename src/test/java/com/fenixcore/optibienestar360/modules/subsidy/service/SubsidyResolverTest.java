package com.fenixcore.optibienestar360.modules.subsidy.service;

import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.entity.SubsidyMode;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyBeneficiary;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyBeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubsidyResolver} — the read-only view of active
 * subsidies consumed by the solvency engine and the beneficiary biller.
 * Highest percentage wins; subsidies never accumulate.
 */
@ExtendWith(MockitoExtension.class)
class SubsidyResolverTest {

    private static final LocalDate ON = LocalDate.of(2026, 6, 15);

    @Mock private SubsidyRepository repository;
    @Mock private SubsidyBeneficiaryRepository beneficiaryRepository;
    @Mock private OrganizationRepository organizationRepository;
    @InjectMocks private SubsidyResolver resolver;

    private final Organization organization = new Organization();

    @BeforeEach
    void defaultModeEnablesBothFees() {
        organization.setSubsidyMode(SubsidyMode.MONTHLY_AND_INSCRIPTION);
        lenient().when(organizationRepository.findSingleton()).thenReturn(organization);
    }

    @Test
    void fullMonthlyExoneration_true_whenActiveHundredPercent() {
        when(repository.findActiveForMember(1L, ON)).thenReturn(List.of(subsidy("100", null)));

        assertThat(resolver.fullMonthlyExoneration(1L, ON)).isTrue();
    }

    @Test
    void fullMonthlyExoneration_false_whenOnlyPartial() {
        when(repository.findActiveForMember(1L, ON)).thenReturn(List.of(subsidy("50", null)));

        assertThat(resolver.fullMonthlyExoneration(1L, ON)).isFalse();
    }

    @Test
    void fullMonthlyExoneration_false_whenOnlyInscriptionCovered() {
        when(repository.findActiveForMember(1L, ON)).thenReturn(List.of(subsidy(null, "100")));

        assertThat(resolver.fullMonthlyExoneration(1L, ON)).isFalse();
    }

    @Test
    void monthlyPercentage_highestWins() {
        when(repository.findActiveForMember(1L, ON))
                .thenReturn(List.of(subsidy("50", null), subsidy("80", null), subsidy(null, "100")));

        assertThat(resolver.monthlyPercentage(1L, ON)).contains(new BigDecimal("80"));
    }

    @Test
    void netMonthlyFee_appliesBestPercentage() {
        when(repository.findActiveForMember(1L, ON)).thenReturn(List.of(subsidy("50", null)));

        assertThat(resolver.netMonthlyFee(new BigDecimal("20.00"), 1L, ON))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void netMonthlyFee_returnsGross_whenNoSubsidy() {
        when(repository.findActiveForMember(1L, ON)).thenReturn(List.of());

        assertThat(resolver.netMonthlyFee(new BigDecimal("20.00"), 1L, ON))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void beneficiaryInscriptionExonerated_true_whenHundredPercent() {
        SubsidyBeneficiary row = new SubsidyBeneficiary();
        row.setInscriptionPercentage(new BigDecimal("100"));
        when(beneficiaryRepository.findActiveForBeneficiary(eq(9L), any())).thenReturn(List.of(row));

        assertThat(resolver.beneficiaryInscriptionExonerated(9L, ON)).isTrue();
    }

    @Test
    void beneficiaryInscriptionExonerated_false_whenPartial() {
        SubsidyBeneficiary row = new SubsidyBeneficiary();
        row.setInscriptionPercentage(new BigDecimal("40"));
        when(beneficiaryRepository.findActiveForBeneficiary(eq(9L), any())).thenReturn(List.of(row));

        assertThat(resolver.beneficiaryInscriptionExonerated(9L, ON)).isFalse();
    }

    // ─── Organization subsidy mode (hub ADR 0041) ─────────────────────────────

    @Test
    void monthlyPercentage_ignoresManualSubsidies_whenModeDisablesMonthly() {
        organization.setSubsidyMode(SubsidyMode.INSCRIPTION);
        when(repository.findActiveForMember(1L, ON)).thenReturn(List.of(subsidy("80", null)));

        assertThat(resolver.monthlyPercentage(1L, ON)).isEmpty();
        assertThat(resolver.fullMonthlyExoneration(1L, ON)).isFalse();
    }

    @Test
    void referralRewards_applyEvenWhenModeIsDisabled() {
        organization.setSubsidyMode(SubsidyMode.DISABLED);
        Subsidy manual = subsidy("80", null);
        Subsidy reward = subsidy("100", null);
        reward.setOrigin(Subsidy.Origin.REFERRAL_REWARD);
        when(repository.findActiveForMember(1L, ON)).thenReturn(List.of(manual, reward));

        assertThat(resolver.monthlyPercentage(1L, ON)).contains(new BigDecimal("100"));
        assertThat(resolver.fullMonthlyExoneration(1L, ON)).isTrue();
    }

    @Test
    void beneficiaryInscriptionExonerated_false_whenModeDisablesInscription() {
        organization.setSubsidyMode(SubsidyMode.MONTHLY);

        assertThat(resolver.beneficiaryInscriptionExonerated(9L, ON)).isFalse();
    }

    @Test
    void batchSweep_onlyCountsReferralRewards_whenModeDisablesMonthly() {
        organization.setSubsidyMode(SubsidyMode.DISABLED);
        when(repository.memberIdsWithFullMonthlyExoneration(List.of(1L), ON,
                EnumSet.of(Subsidy.Origin.REFERRAL_REWARD))).thenReturn(Set.of(1L));

        assertThat(resolver.memberIdsWithFullMonthlyExoneration(List.of(1L), ON)).containsExactly(1L);
    }

    @Test
    void batchSweep_countsEveryOrigin_whenModeEnablesMonthly() {
        resolver.memberIdsWithFullMonthlyExoneration(List.of(1L), ON);

        verify(repository).memberIdsWithFullMonthlyExoneration(List.of(1L), ON,
                EnumSet.allOf(Subsidy.Origin.class));
    }

    private static Subsidy subsidy(String monthlyPct, String inscriptionPct) {
        Subsidy s = new Subsidy();
        s.setMonthlyPercentage(monthlyPct != null ? new BigDecimal(monthlyPct) : null);
        s.setInscriptionPercentage(inscriptionPct != null ? new BigDecimal(inscriptionPct) : null);
        return s;
    }
}
