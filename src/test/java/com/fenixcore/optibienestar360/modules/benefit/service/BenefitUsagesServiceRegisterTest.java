package com.fenixcore.optibienestar360.modules.benefit.service;

import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageRegisterRequest;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage;
import com.fenixcore.optibienestar360.modules.benefit.mapper.BenefitUsageMapper;
import com.fenixcore.optibienestar360.modules.benefit.repository.BenefitUsageRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.catalog.repository.ProductCategoryRepository;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.promotion.entity.MembershipBenefitGrant;
import com.fenixcore.optibienestar360.modules.promotion.service.MembershipBenefitGrantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the ally-membership guard of {@link BenefitUsagesService#register}:
 * only an active OWNER/STAFF of the target ally may register a usage, the
 * usage is stamped with the caller's own membership, and the offer must
 * belong to that same ally.
 */
@ExtendWith(MockitoExtension.class)
class BenefitUsagesServiceRegisterTest {

    @Mock private BenefitUsageRepository usageRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private AllyRepository allyRepository;
    @Mock private AllyProductRepository allyProductRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ProductCategoryRepository productCategoryRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private BenefitUsageMapper mapper;
    @Mock private ConversionEnricher conversionEnricher;
    @Mock private MembershipBenefitGrantService benefitGrantService;

    private final UUID actorUuid = UUID.randomUUID();
    private final UUID membershipUuid = UUID.randomUUID();
    private final UUID categoryUuid = UUID.randomUUID();
    private final Ally ally = ally();

    private BenefitUsagesService sut() {
        return new BenefitUsagesService(usageRepository, membershipRepository, allyRepository,
                allyProductRepository, allyUserRepository, currencyRepository, productCategoryRepository, beneficiaryRepository, mapper, conversionEnricher,
                benefitGrantService);
    }

    @Test
    void register_callerNotWorkingForTheAlly_isForbidden() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().register(request(null, null), actorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("benefit_usage.ally.not_allowed");
        verify(usageRepository, never()).save(any());
    }

    @Test
    void register_viewer_isForbidden() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(operator(AllyRole.VIEWER)));

        assertThatThrownBy(() -> sut().register(request(null, null), actorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("benefit_usage.ally.not_allowed");
        verify(usageRepository, never()).save(any());
    }

    @Test
    void register_withAnotherOperatorsMembership_isForbidden() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(operator(AllyRole.STAFF)));

        assertThatThrownBy(() -> sut().register(request(null, UUID.randomUUID()), actorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("benefit_usage.ally.not_allowed");
        verify(usageRepository, never()).save(any());
    }

    @Test
    void register_offerOfAnotherAlly_isRejected() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(operator(AllyRole.STAFF)));
        when(membershipRepository.findByUuid(membershipUuid)).thenReturn(Optional.of(activeMembership()));
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        AllyProduct foreignOffer = new AllyProduct();
        foreignOffer.setUuid(UUID.randomUUID());
        foreignOffer.setAlly(ally());
        when(allyProductRepository.findByUuid(foreignOffer.getUuid())).thenReturn(Optional.of(foreignOffer));

        assertThatThrownBy(() -> sut().register(request(foreignOffer.getUuid(), null), actorUuid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benefit_usage.ally_product.other_ally");
        verify(usageRepository, never()).save(any());
    }

    @Test
    void register_staffOfTheAlly_savesStampedWithTheCallersMembership() {
        AllyUser operator = operator(AllyRole.STAFF);
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(operator));
        when(membershipRepository.findByUuid(membershipUuid)).thenReturn(Optional.of(activeMembership()));
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(productCategoryRepository.findByUuid(categoryUuid)).thenReturn(Optional.of(new ProductCategory()));
        when(usageRepository.save(any(BenefitUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        sut().register(request(null, operator.getUuid()), actorUuid);

        ArgumentCaptor<BenefitUsage> saved = ArgumentCaptor.forClass(BenefitUsage.class);
        verify(usageRepository).save(saved.capture());
        assertThat(saved.getValue().getAllyUser()).isSameAs(operator);
        assertThat(saved.getValue().getAlly()).isSameAs(ally);
    }

    // ─── Campaign vouchers (V257, hub ADR 0038 §7) ──────────────────────────────

    @Test
    void register_withAFreeVoucher_redeemsIt_takesItsOffer_andZeroesTheCopay() {
        AllyUser operator = operator(AllyRole.STAFF);
        Membership membership = activeMembership();
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(operator));
        when(membershipRepository.findByUuid(membershipUuid)).thenReturn(Optional.of(membership));
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        Product product = new Product();
        product.setProductCategory(new ProductCategory());
        AllyProduct offer = new AllyProduct();
        offer.setUuid(UUID.randomUUID());
        offer.setAlly(ally);
        offer.setProduct(product);
        MembershipBenefitGrant grant = new MembershipBenefitGrant();
        grant.setAllyProduct(offer);
        UUID grantUuid = UUID.randomUUID();
        when(benefitGrantService.redeem(eq(grantUuid), eq(membership), eq(ally), isNull())).thenReturn(grant);
        when(benefitGrantService.copayAfter(eq(grant), isNull(), isNull(), isNull()))
                .thenReturn(new MembershipBenefitGrantService.Copay(BigDecimal.ZERO, "USD"));
        Currency usd = new Currency();
        usd.setCode("USD");
        when(currencyRepository.findByCode("USD")).thenReturn(Optional.of(usd));
        when(conversionEnricher.snapshotToOfficial(any(), any(), any()))
                .thenReturn(new ConversionEnricher.Conversion(null, null, null, null));
        when(usageRepository.save(any(BenefitUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        sut().register(withGrant(grantUuid), actorUuid);

        ArgumentCaptor<BenefitUsage> saved = ArgumentCaptor.forClass(BenefitUsage.class);
        verify(usageRepository).save(saved.capture());
        assertThat(saved.getValue().getBenefitGrant()).isSameAs(grant);
        assertThat(saved.getValue().getAllyProduct()).isSameAs(offer);
        assertThat(saved.getValue().getCopayAmount()).isEqualByComparingTo("0");
        assertThat(saved.getValue().getCopayCurrency()).isSameAs(usd);
    }

    @Test
    void register_withAnUnusableVoucher_savesNothing() {
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(operator(AllyRole.STAFF)));
        when(membershipRepository.findByUuid(membershipUuid)).thenReturn(Optional.of(activeMembership()));
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        UUID grantUuid = UUID.randomUUID();
        when(benefitGrantService.redeem(eq(grantUuid), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("benefit_grant.expired"));

        assertThatThrownBy(() -> sut().register(withGrant(grantUuid), actorUuid))
                .hasMessage("benefit_grant.expired");
        verify(usageRepository, never()).save(any());
    }

    private BenefitUsageRegisterRequest withGrant(UUID grantUuid) {
        return new BenefitUsageRegisterRequest(membershipUuid, ally.getUuid(), null, null,
                null, null, null, null, null, null, null, null, null, grantUuid);
    }

    // ─── fixtures ───────────────────────────────────────────────────────────────

    private BenefitUsageRegisterRequest request(UUID allyProductUuid, UUID allyUserUuid) {
        return new BenefitUsageRegisterRequest(membershipUuid, ally.getUuid(), allyProductUuid, allyUserUuid,
                allyProductUuid == null ? categoryUuid : null, null,
                null, null, null, null, null, null, null, null);
    }

    private static Ally ally() {
        Ally a = new Ally();
        a.setUuid(UUID.randomUUID());
        return a;
    }

    private AllyUser operator(AllyRole role) {
        AllyUser m = new AllyUser();
        m.setUuid(UUID.randomUUID());
        m.setAlly(ally);
        m.setAllyRole(role);
        return m;
    }

    private static Membership activeMembership() {
        Membership m = new Membership();
        m.setStatus(Membership.LifecycleStatus.ACTIVE.name());
        return m;
    }
}
