package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAward;
import com.fenixcore.optibienestar360.modules.promoter.entity.CompetitiveCommissionAwardSettlement;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CompetitiveCommissionAwardSettlementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetitiveCommissionAwardsServiceTest {

    @Mock private CompetitiveCommissionAwardRepository awardRepository;
    @Mock private CompetitiveCommissionAwardSettlementRepository settlementRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private CurrencyConversionService conversionService;

    private CompetitiveCommissionAwardsService sut() {
        Currency official = new Currency();
        official.setId(1L);
        Organization organization = new Organization();
        organization.setOfficialCurrency(official);
        lenient().when(organizationRepository.findSingleton()).thenReturn(organization);
        return new CompetitiveCommissionAwardsService(
                awardRepository, settlementRepository, paymentRepository, organizationRepository, conversionService);
    }

    private static Currency currency(long id) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setCode("USD");
        return currency;
    }

    private static CompetitiveCommissionAward award(BigDecimal amount) {
        CompetitiveCommissionAward award = new CompetitiveCommissionAward();
        award.setUuid(UUID.randomUUID());
        award.setAmount(amount);
        award.setCurrency(currency(1L)); // same as official — no conversion path exercised here
        award.setStatus("PENDING");
        return award;
    }

    private static CompetitiveCommissionAwardSettlement settlement(CompetitiveCommissionAward award, BigDecimal amount) {
        CompetitiveCommissionAwardSettlement settlement = new CompetitiveCommissionAwardSettlement();
        settlement.setUuid(UUID.randomUUID());
        settlement.setAward(award);
        settlement.setAmount(amount);
        settlement.setCurrency(currency(1L));
        settlement.setStatus("PENDING");
        return settlement;
    }

    @Test
    void paySettlement_fullyCoveringAmount_alsoMarksAwardPaid() {
        CompetitiveCommissionAward award = award(new BigDecimal("100.00"));
        CompetitiveCommissionAwardSettlement settlement = settlement(award, new BigDecimal("100.00"));
        when(settlementRepository.findByUuid(settlement.getUuid())).thenReturn(Optional.of(settlement));
        when(settlementRepository.findByAward_IdAndActiveTrueAndStatus(any(), org.mockito.ArgumentMatchers.eq("PAID")))
                .thenReturn(List.of(settlement)); // after status flip, this cut counts as PAID for the coverage check

        sut().paySettlement(settlement.getUuid(), "REF-1", null);

        assertThat(settlement.getStatus()).isEqualTo("PAID");
        assertThat(settlement.getPayoutReference()).isEqualTo("REF-1");
        assertThat(award.getStatus()).isEqualTo("PAID");
        assertThat(award.getPaidAt()).isNotNull();
    }

    @Test
    void paySettlement_partialAmount_leavesAwardPending() {
        CompetitiveCommissionAward award = award(new BigDecimal("100.00"));
        CompetitiveCommissionAwardSettlement settlement = settlement(award, new BigDecimal("40.00"));
        when(settlementRepository.findByUuid(settlement.getUuid())).thenReturn(Optional.of(settlement));
        when(settlementRepository.findByAward_IdAndActiveTrueAndStatus(any(), org.mockito.ArgumentMatchers.eq("PAID")))
                .thenReturn(List.of(settlement));

        sut().paySettlement(settlement.getUuid(), "REF-1", null);

        assertThat(settlement.getStatus()).isEqualTo("PAID");
        assertThat(award.getStatus()).isEqualTo("PENDING"); // only 40 of 100 covered
    }

    @Test
    void paySettlement_alreadyPaid_rejected() {
        CompetitiveCommissionAwardSettlement settlement = settlement(award(BigDecimal.TEN), BigDecimal.TEN);
        settlement.setStatus("PAID");
        when(settlementRepository.findByUuid(settlement.getUuid())).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> sut().paySettlement(settlement.getUuid(), "REF-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_commission_award_settlement.pay.not_pending");
    }

    @Test
    void voidAward_rejectsWhenAlreadyPaid() {
        CompetitiveCommissionAward award = award(BigDecimal.TEN);
        award.setStatus("PAID");
        when(awardRepository.findByUuid(award.getUuid())).thenReturn(Optional.of(award));

        assertThatThrownBy(() -> sut().voidAward(award.getUuid(), "conducta antideportiva"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("competitive_award.already_paid");
    }

    @Test
    void voidAward_voidsPendingSettlementsToo() {
        CompetitiveCommissionAward award = award(new BigDecimal("100.00"));
        CompetitiveCommissionAwardSettlement pendingSettlement = settlement(award, new BigDecimal("100.00"));
        when(awardRepository.findByUuid(award.getUuid())).thenReturn(Optional.of(award));
        when(settlementRepository.findByAward_IdAndActiveTrueAndStatus(any(), org.mockito.ArgumentMatchers.eq("PENDING")))
                .thenReturn(List.of(pendingSettlement));

        sut().voidAward(award.getUuid(), "regla desactivada");

        assertThat(award.getStatus()).isEqualTo("VOIDED");
        assertThat(award.getVoidReason()).isEqualTo("regla desactivada");
        assertThat(pendingSettlement.getStatus()).isEqualTo("VOIDED");
    }
}
