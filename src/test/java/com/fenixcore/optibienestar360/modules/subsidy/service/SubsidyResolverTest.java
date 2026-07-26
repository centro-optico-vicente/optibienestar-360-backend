package com.fenixcore.optibienestar360.modules.subsidy.service;

import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyBeneficiary;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyBeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @InjectMocks private SubsidyResolver resolver;

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

    private static Subsidy subsidy(String monthlyPct, String inscriptionPct) {
        Subsidy s = new Subsidy();
        s.setMonthlyPercentage(monthlyPct != null ? new BigDecimal(monthlyPct) : null);
        s.setInscriptionPercentage(inscriptionPct != null ? new BigDecimal(inscriptionPct) : null);
        return s;
    }
}
