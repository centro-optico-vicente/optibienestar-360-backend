package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant.GrantStatus;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipPromotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitKind;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;
import com.fenixcore.optibienestar360.modules.promotion.entity.PromotionRedemption;
import com.fenixcore.optibienestar360.modules.promotion.repository.MembershipBenefitGrantRepository;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import com.fenixcore.optibienestar360.modules.validator.dto.ValidationResultDto;
import com.fenixcore.optibienestar360.modules.validator.dto.ValidationResultDto.ValidationStatus;
import com.fenixcore.optibienestar360.modules.validator.service.ValidatorCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ally vouchers of campaign promotions (V257, hub ADR 0038 §7). */
@ExtendWith(MockitoExtension.class)
class MembershipBenefitGrantServiceTest {

    @Mock private MembershipBenefitGrantRepository repository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CurrencyConversionService currencyConversionService;
    @Mock private ValidatorCacheService validatorCacheService;

    private MembershipBenefitGrantService service() {
        return new MembershipBenefitGrantService(repository, allyUserRepository, membershipRepository, memberRepository,
                campaignRepository, promotionRepository, currencyRepository, currencyConversionService,
                validatorCacheService);
    }

    private static Ally ally(long id) {
        Ally a = new Ally();
        a.setId(id);
        a.setUuid(UUID.randomUUID());
        return a;
    }

    private static AllyProduct offer(long id, Ally ally) {
        AllyProduct ap = new AllyProduct();
        ap.setId(id);
        ap.setUuid(UUID.randomUUID());
        ap.setAlly(ally);
        return ap;
    }

    private static Membership membership(long id) {
        Membership m = new Membership();
        m.setId(id);
        m.setUuid(UUID.randomUUID());
        return m;
    }

    private static MembershipBenefitGrant grant(Membership membership, Ally ally, AllyProduct offer, int quantity) {
        MembershipBenefitGrant g = new MembershipBenefitGrant();
        g.setUuid(UUID.randomUUID());
        g.setMembership(membership);
        g.setAlly(ally);
        g.setAllyProduct(offer);
        g.setBenefitType(BenefitType.FREE);
        g.setQuantity(quantity);
        g.setRemainingQty(quantity);
        g.setGrantStatus(GrantStatus.AVAILABLE);
        g.setGrantedAt(Instant.now());
        return g;
    }

    @Test
    void confirmedAllyBenefitSlot_createsAnAvailableVoucher_snapshottingTheBenefit() {
        Ally ally = ally(1L);
        AllyProduct offer = offer(10L, ally);
        Promotion promotion = new Promotion();
        promotion.setBenefitKind(BenefitKind.ALLY_BENEFIT);
        promotion.setAlly(ally);
        promotion.setAllyProduct(offer);
        promotion.setBenefitType(BenefitType.PERCENTAGE);
        promotion.setBenefitPct(new BigDecimal("50.00"));
        promotion.setBenefitQuantity(2);
        Membership membership = membership(5L);
        MembershipPromotion mp = new MembershipPromotion();
        mp.setId(40L);
        mp.setMembership(membership);
        PromotionRedemption r = new PromotionRedemption();
        r.setPromotion(promotion);
        r.setMembershipPromotion(mp);
        Instant confirmed = Instant.now();
        r.setConfirmedAt(confirmed);
        r.setUsageEndsAt(confirmed.plus(30, ChronoUnit.DAYS));
        when(repository.findByMembershipPromotion_Id(40L)).thenReturn(Optional.empty());
        when(repository.save(any(MembershipBenefitGrant.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipBenefitGrant g = service().grantFor(r);

        assertThat(g.getGrantStatus()).isEqualTo(GrantStatus.AVAILABLE);
        assertThat(g.getAlly()).isSameAs(ally);
        assertThat(g.getAllyProduct()).isSameAs(offer);
        assertThat(g.getBenefitPct()).isEqualByComparingTo("50.00");
        assertThat(g.getQuantity()).isEqualTo(2);
        assertThat(g.getRemainingQty()).isEqualTo(2);
        assertThat(g.getExpiresAt()).isEqualTo(r.getUsageEndsAt());
        verify(validatorCacheService).evictForMembership(membership);
    }

    @Test
    void redeem_partialThenFull_marksRedeemedAtZero_andEvictsTheValidatorCache() {
        Ally ally = ally(1L);
        AllyProduct offer = offer(10L, ally);
        Membership membership = membership(5L);
        MembershipBenefitGrant g = grant(membership, ally, offer, 2);
        when(repository.findForUpdateByUuid(g.getUuid())).thenReturn(Optional.of(g));

        service().redeem(g.getUuid(), membership, ally, offer);
        assertThat(g.getRemainingQty()).isEqualTo(1);
        assertThat(g.getGrantStatus()).isEqualTo(GrantStatus.AVAILABLE);

        service().redeem(g.getUuid(), membership, ally, null);
        assertThat(g.getRemainingQty()).isZero();
        assertThat(g.getGrantStatus()).isEqualTo(GrantStatus.REDEEMED);
        assertThat(g.getRedeemedAt()).isNotNull();

        assertThatThrownBy(() -> service().redeem(g.getUuid(), membership, ally, offer))
                .hasMessage("benefit_grant.not_available");
        verify(validatorCacheService, org.mockito.Mockito.times(2)).evictForMembership(membership);
    }

    @Test
    void redeem_rejectsAnExpiredVoucher_anotherAlly_anotherMembership_andAnotherOffer() {
        Ally ally = ally(1L);
        AllyProduct offer = offer(10L, ally);
        Membership membership = membership(5L);
        MembershipBenefitGrant g = grant(membership, ally, offer, 1);
        when(repository.findForUpdateByUuid(g.getUuid())).thenReturn(Optional.of(g));

        g.setExpiresAt(Instant.now().minusSeconds(60));
        assertThatThrownBy(() -> service().redeem(g.getUuid(), membership, ally, offer))
                .hasMessage("benefit_grant.expired");

        g.setExpiresAt(null);
        assertThatThrownBy(() -> service().redeem(g.getUuid(), membership, ally(2L), offer))
                .hasMessage("benefit_grant.other_ally");
        assertThatThrownBy(() -> service().redeem(g.getUuid(), membership(6L), ally, offer))
                .hasMessage("benefit_grant.other_membership");
        assertThatThrownBy(() -> service().redeem(g.getUuid(), membership, ally, offer(11L, ally)))
                .hasMessage("benefit_grant.other_product");
        assertThat(g.getRemainingQty()).isEqualTo(1);
    }

    @Test
    void validator_listsOnlyTheCallersAvailableUnexpiredVouchers() {
        Ally mine = ally(1L);
        AllyUser operator = new AllyUser();
        operator.setAlly(mine);
        UUID actor = UUID.randomUUID();
        Membership membership = membership(5L);
        Promotion promotion = new Promotion();
        promotion.setUuid(UUID.randomUUID());
        promotion.setName("Consulta gratis");
        Campaign campaign = new Campaign();
        campaign.setUuid(UUID.randomUUID());
        campaign.setName("Navidad");
        promotion.setCampaign(campaign);
        MembershipBenefitGrant usable = grant(membership, mine, offer(10L, mine), 1);
        usable.setPromotion(promotion);
        MembershipBenefitGrant expired = grant(membership, mine, offer(10L, mine), 1);
        expired.setPromotion(promotion);
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(allyUserRepository.findActiveByUserUuid(actor)).thenReturn(List.of(operator));
        when(membershipRepository.findByUuid(membership.getUuid())).thenReturn(Optional.of(membership));
        when(repository.findForMembershipAndAllies(eq(5L), eq(GrantStatus.AVAILABLE), eq(Set.of(1L))))
                .thenReturn(List.of(usable, expired));
        ValidationResultDto answer = new ValidationResultDto(ValidationStatus.ACTIVE, "V", "123",
                null, null, null, null, null, membership.getUuid(), null, null, null, null,
                null, null, null, null, true, null);

        ValidationResultDto enriched = service().withGrants(answer, actor);

        assertThat(enriched.benefitGrants()).hasSize(1);
        assertThat(enriched.benefitGrants().get(0).uuid()).isEqualTo(usable.getUuid());
        assertThat(enriched.benefitGrants().get(0).campaign().uuid()).isEqualTo(campaign.getUuid());
        assertThat(enriched.status()).isEqualTo(ValidationStatus.ACTIVE);
        assertThat(enriched.cached()).isTrue();
    }

    @Test
    void copay_free_percentage_andFlatInAnotherCurrency() {
        MembershipBenefitGrant free = grant(membership(5L), ally(1L), offer(10L, ally(1L)), 1);
        assertThat(service().copayAfter(free, new BigDecimal("20.00"), "USD", null).amount()).isEqualByComparingTo("0");
        assertThat(service().copayAfter(free, null, null, "VES").currencyCode()).isEqualTo("VES");

        MembershipBenefitGrant pct = grant(membership(5L), ally(1L), offer(10L, ally(1L)), 1);
        pct.setBenefitType(BenefitType.PERCENTAGE);
        pct.setBenefitPct(new BigDecimal("25.00"));
        assertThat(service().copayAfter(pct, new BigDecimal("20.00"), "USD", null).amount()).isEqualByComparingTo("15.00");

        Currency usd = new Currency();
        usd.setId(1L);
        usd.setCode("USD");
        Currency ves = new Currency();
        ves.setId(2L);
        ves.setCode("VES");
        MembershipBenefitGrant flat = grant(membership(5L), ally(1L), offer(10L, ally(1L)), 1);
        flat.setBenefitType(BenefitType.FLAT);
        flat.setBenefitAmount(new BigDecimal("2.00"));
        flat.setBenefitCurrency(usd);
        when(currencyRepository.findByCode("VES")).thenReturn(Optional.of(ves));
        when(currencyConversionService.convert(any(BigDecimal.class), eq(usd), eq(ves), any(Instant.class)))
                .thenReturn(new CurrencyConversionService.ConversionResult(new BigDecimal("2.00"),
                        new BigDecimal("80.00"), new BigDecimal("40"), java.time.LocalDate.now()));
        assertThat(service().copayAfter(flat, new BigDecimal("100.00"), "VES", null).amount()).isEqualByComparingTo("20.00");
        assertThat(service().copayAfter(flat, new BigDecimal("50.00"), "VES", null).amount()).isEqualByComparingTo("0");
    }
}
