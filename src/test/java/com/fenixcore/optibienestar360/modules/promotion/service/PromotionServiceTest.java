package com.fenixcore.optibienestar360.modules.promotion.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionDto;
import com.fenixcore.optibienestar360.modules.promotion.dto.PromotionRequest;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.AppliesTo;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitKind;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.BenefitType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.DiscountType;
import com.fenixcore.optibienestar360.modules.promotion.entity.Promotion.Kind;
import com.fenixcore.optibienestar360.modules.promotion.repository.PromotionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock private PromotionRepository repository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private PlanRepository planRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private AllyRepository allyRepository;
    @Mock private AllyProductRepository allyProductRepository;
    @Mock private EntityManager entityManager;

    private PromotionService service() {
        return new PromotionService(repository, campaignRepository, planRepository, currencyRepository,
                allyRepository, allyProductRepository, entityManager);
    }

    private static Campaign campaign() {
        Campaign c = new Campaign();
        c.setUuid(UUID.randomUUID());
        c.setName("Octubre");
        return c;
    }

    private static PromotionRequest request(Kind kind, AppliesTo appliesTo, boolean requiresCode,
                                            boolean acceptsMemberCode, BigDecimal rewardPct, Integer rewardCycles,
                                            List<UUID> planUuids) {
        return new PromotionRequest("Inscripción 50%", null, kind, null, new BigDecimal("50.00"), null, null, appliesTo,
                3, false, true, 100, requiresCode, false, acceptsMemberCode, false, rewardPct, rewardCycles, planUuids,
                null, null, null, null, null, null, null, null);
    }

    private static PromotionRequest discountRequest(DiscountType type, BigDecimal pct, BigDecimal amount, UUID currencyUuid) {
        return new PromotionRequest("Inscripción $5", null, Kind.ACQUISITION, type, pct, amount, currencyUuid,
                AppliesTo.BOTH, null, false, false, null, false, false, false, false, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void create_rejectsAMaxWithoutOwnQuota() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        PromotionRequest req = new PromotionRequest("Cupo", null, Kind.ACQUISITION, null, new BigDecimal("10.00"), null,
                null, AppliesTo.MONTHLY, null, false, false, 50, false, false, false, false, null, null, null,
                null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service().create(campaign.getUuid(), req))
                .hasMessage("promotion.max_redemptions.requires_own_quota");
    }

    private static Currency currency(String code) {
        Currency c = new Currency();
        c.setUuid(UUID.randomUUID());
        c.setCode(code);
        return c;
    }

    @Test
    void create_flatDiscount_storesAmountAndCurrency_withoutPercentage() {
        Campaign campaign = campaign();
        Currency usd = currency("USD");
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        when(currencyRepository.findByUuid(usd.getUuid())).thenReturn(Optional.of(usd));
        when(repository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionDto dto = service().create(campaign.getUuid(),
                discountRequest(DiscountType.FLAT, null, new BigDecimal("5.00"), usd.getUuid()));

        assertThat(dto.discountType()).isEqualTo(DiscountType.FLAT);
        assertThat(dto.discountPct()).isNull();
        assertThat(dto.discountAmount()).isEqualByComparingTo("5.00");
        assertThat(dto.discountCurrency_Code()).isEqualTo("USD");
    }

    @Test
    void create_nullDiscountType_meansPercentage() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        when(repository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionDto dto = service().create(campaign.getUuid(),
                discountRequest(null, new BigDecimal("10.00"), null, null));

        assertThat(dto.discountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(dto.discountPct()).isEqualByComparingTo("10.00");
        assertThat(dto.discountAmount()).isNull();
    }

    @Test
    void create_rejectsAnIncoherentDiscount() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        UUID usd = UUID.randomUUID();

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                discountRequest(DiscountType.FLAT, null, new BigDecimal("5.00"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promotion.discount.flat_incomplete");
        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                discountRequest(DiscountType.FLAT, new BigDecimal("10.00"), new BigDecimal("5.00"), usd)))
                .hasMessage("promotion.discount.flat_incomplete");
        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                discountRequest(DiscountType.PERCENTAGE, null, null, null)))
                .hasMessage("promotion.discount.pct_required");
        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                discountRequest(DiscountType.PERCENTAGE, new BigDecimal("10.00"), new BigDecimal("5.00"), usd)))
                .hasMessage("promotion.discount.pct_with_amount");
        verify(repository, never()).save(any());
    }

    @Test
    void create_persistsPromotionUnderCampaign_withEligiblePlans() {
        Campaign campaign = campaign();
        Plan plan = new Plan();
        plan.setUuid(UUID.randomUUID());
        plan.setName("Individual");
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(repository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionDto dto = service().create(campaign.getUuid(),
                request(Kind.ACQUISITION, AppliesTo.BOTH, true, true, new BigDecimal("100.00"), 1, List.of(plan.getUuid())));

        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCampaign()).isSameAs(campaign);
        assertThat(saved.getValue().getPlans()).containsExactly(plan);
        assertThat(saved.getValue().getReferrerRewardPct()).isEqualByComparingTo("100.00");
        assertThat(dto.plans()).hasSize(1);
    }

    @Test
    void create_rejectsRecoveryOnInscription() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                request(Kind.RECOVERY, AppliesTo.BOTH, false, false, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promotion.recovery.monthly_only");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsRequiredCodeWithoutAcceptedOwner() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                request(Kind.ACQUISITION, AppliesTo.MONTHLY, true, false, null, null, null)))
                .hasMessage("promotion.code.owner_required");
    }

    @Test
    void create_rejectsReferrerRewardWithoutMemberCodes() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                request(Kind.ACQUISITION, AppliesTo.MONTHLY, false, false, new BigDecimal("50.00"), 1, null)))
                .hasMessage("promotion.referrer_reward.member_code_required");
    }

    @Test
    void cloneInto_copiesPromotionsToTheRelaunchedCampaign_withCountersReset() {
        Campaign source = campaign();
        Campaign target = campaign();
        Promotion original = new Promotion();
        original.setCampaign(source);
        original.setName("3 meses 20%");
        original.setKind(Kind.ACQUISITION);
        original.setDiscountPct(new BigDecimal("20.00"));
        original.setAppliesTo(AppliesTo.MONTHLY);
        original.setCycles(3);
        original.setOwnQuota(true);
        original.setMaxRedemptions(50);
        original.setRedemptionsCount(42);
        original.setConfirmedCount(40);
        original.setPlans(Set.of(new Plan()));
        when(repository.findByCampaignAndActiveTrueOrderByCreatedAtAsc(source)).thenReturn(List.of(original));

        service().cloneInto(source, target);

        ArgumentCaptor<Promotion> saved = ArgumentCaptor.forClass(Promotion.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getCampaign()).isSameAs(target);
        assertThat(saved.getValue().getCycles()).isEqualTo(3);
        assertThat(saved.getValue().getRedemptionsCount()).isZero();
        assertThat(saved.getValue().getConfirmedCount()).isZero();
        assertThat(saved.getValue().isOwnQuota()).isTrue();
        assertThat(saved.getValue().getMaxRedemptions()).isEqualTo(50);
        assertThat(saved.getValue().getPlans()).hasSize(1);
    }

    // ─── Ally benefits (V257, hub ADR 0038 §7-§8) ───────────────────────────

    private static Ally ally(long id) {
        Ally a = new Ally();
        a.setId(id);
        a.setUuid(UUID.randomUUID());
        a.setName("Óptica " + id);
        return a;
    }

    private static AllyProduct offer(Ally ally) {
        AllyProduct ap = new AllyProduct();
        ap.setId(ally.getId() * 10);
        ap.setUuid(UUID.randomUUID());
        ap.setAlly(ally);
        return ap;
    }

    private static PromotionRequest allyBenefit(UUID allyUuid, UUID allyProductUuid, BenefitType type, BigDecimal pct) {
        return new PromotionRequest("Consulta gratis", null, Kind.ACQUISITION, null, null, null, null, null,
                null, false, false, null, false, false, false, false, null, null, null,
                BenefitKind.ALLY_BENEFIT, allyUuid, allyProductUuid, type, pct, null, null, 1);
    }

    @Test
    void exclusiveCampaign_forcesItsAllyOnTheLine_andRejectsAnother() {
        Campaign campaign = campaign();
        Ally owner = ally(1L);
        campaign.setAlly(owner);
        AllyProduct ownerOffer = offer(owner);
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        when(allyProductRepository.findByUuid(ownerOffer.getUuid())).thenReturn(Optional.of(ownerOffer));
        when(repository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionDto dto = service().create(campaign.getUuid(),
                allyBenefit(null, ownerOffer.getUuid(), BenefitType.FREE, null));
        assertThat(dto.benefitKind()).isEqualTo(BenefitKind.ALLY_BENEFIT);
        assertThat(dto.ally().uuid()).isEqualTo(owner.getUuid());
        assertThat(dto.appliesTo()).isNull();

        Ally other = ally(2L);
        when(allyRepository.findByUuid(other.getUuid())).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                allyBenefit(other.getUuid(), ownerOffer.getUuid(), BenefitType.FREE, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promotion.ally.campaign_exclusive");
    }

    @Test
    void multiAllyCampaign_eachLineNamesItsAlly_andItsOwnOffer() {
        Campaign campaign = campaign();
        Ally first = ally(1L);
        Ally second = ally(2L);
        AllyProduct firstOffer = offer(first);
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));
        when(allyRepository.findByUuid(first.getUuid())).thenReturn(Optional.of(first));
        when(allyRepository.findByUuid(second.getUuid())).thenReturn(Optional.of(second));
        when(allyProductRepository.findByUuid(firstOffer.getUuid())).thenReturn(Optional.of(firstOffer));
        when(repository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionDto dto = service().create(campaign.getUuid(),
                allyBenefit(first.getUuid(), firstOffer.getUuid(), BenefitType.PERCENTAGE, new BigDecimal("30.00")));
        assertThat(dto.ally().uuid()).isEqualTo(first.getUuid());
        assertThat(dto.benefitPct()).isEqualByComparingTo("30.00");

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                allyBenefit(second.getUuid(), firstOffer.getUuid(), BenefitType.FREE, null)))
                .hasMessage("promotion.ally_product.other_ally");
        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                allyBenefit(null, firstOffer.getUuid(), BenefitType.FREE, null)))
                .hasMessage("promotion.ally.required");
    }

    @Test
    void allyBenefit_rejectsAnIncoherentBenefit() {
        Campaign campaign = campaign();
        when(campaignRepository.findByUuid(campaign.getUuid())).thenReturn(Optional.of(campaign));

        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                allyBenefit(UUID.randomUUID(), UUID.randomUUID(), BenefitType.FREE, new BigDecimal("10.00"))))
                .hasMessage("promotion.ally_benefit.type_incoherent");
        assertThatThrownBy(() -> service().create(campaign.getUuid(),
                allyBenefit(UUID.randomUUID(), UUID.randomUUID(), BenefitType.PERCENTAGE, null)))
                .hasMessage("promotion.ally_benefit.type_incoherent");
        verify(repository, never()).save(any());
    }

    // ─── Edit locks (hub ADR 0038 §4) ───────────────────────────────────────

    /** A live promotion of {@code campaign} reachable through the edit locks (campaign row first, then promotion). */
    private Promotion lockable(Campaign campaign) {
        campaign.setId(40L);
        Promotion p = new Promotion();
        p.setId(41L);
        p.setUuid(UUID.randomUUID());
        p.setName("Navidad");
        p.setKind(Kind.ACQUISITION);
        p.setCampaign(campaign);
        when(repository.findCampaignIdByUuid(p.getUuid())).thenReturn(Optional.of(40L));
        when(campaignRepository.findForUpdateById(40L)).thenReturn(Optional.of(campaign));
        when(repository.findForUpdateByUuid(p.getUuid())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void update_locksTheCampaignThenThePromotion_andKeepsTheCounters() {
        Campaign campaign = campaign();
        Promotion p = lockable(campaign);
        p.setRedemptionsCount(4);
        p.setConfirmedCount(3);
        when(repository.save(any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        PromotionDto dto = service().update(p.getUuid(), discountRequest(null, new BigDecimal("15.00"), null, null));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(campaignRepository, repository, entityManager);
        order.verify(campaignRepository).findForUpdateById(40L);
        order.verify(entityManager).refresh(campaign);
        order.verify(repository).findForUpdateByUuid(p.getUuid());
        order.verify(entityManager).refresh(p);
        assertThat(dto.discountPct()).isEqualByComparingTo("15.00");
        assertThat(dto.redemptionsCount()).isEqualTo(4);
        assertThat(dto.confirmedCount()).isEqualTo(3);
    }

    @Test
    void delete_takesTheSameLocks() {
        Campaign campaign = campaign();
        Promotion p = lockable(campaign);

        service().delete(p.getUuid());

        assertThat(p.isActive()).isFalse();
        verify(campaignRepository).findForUpdateById(40L);
        verify(repository).findForUpdateByUuid(p.getUuid());
    }
}
