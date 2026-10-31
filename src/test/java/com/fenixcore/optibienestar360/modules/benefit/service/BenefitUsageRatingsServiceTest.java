package com.fenixcore.optibienestar360.modules.benefit.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageRatingRequest;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage.UsageStatus;
import com.fenixcore.optibienestar360.modules.benefit.mapper.BenefitUsageMapper;
import com.fenixcore.optibienestar360.modules.benefit.repository.BenefitUsageRepository;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenefitUsageRatingsServiceTest {

    @Mock
    private BenefitUsageRepository usageRepository;
    @Mock
    private BenefitUsageMapper mapper;
    @InjectMocks
    private BenefitUsageRatingsService service;

    private final UUID userUuid = UUID.randomUUID();
    private final UUID usageUuid = UUID.randomUUID();
    private BenefitUsage usage;

    @BeforeEach
    void setUp() {
        Ally ally = new Ally();
        ally.setId(10L);
        usage = new BenefitUsage();
        usage.setAlly(ally);
        usage.setStatus(UsageStatus.REGISTERED.name());
    }

    private void withOffer() {
        Product product = new Product();
        product.setId(30L);
        AllyProduct offer = new AllyProduct();
        offer.setId(20L);
        offer.setProduct(product);
        usage.setAllyProduct(offer);
    }

    @Test
    void rate_withOffer_storesBothRatings_andRefreshesEveryAverage() {
        withOffer();
        when(usageRepository.findByUuidForMemberUser(usageUuid, userUuid)).thenReturn(Optional.of(usage));

        service.rate(userUuid, usageUuid, new BenefitUsageRatingRequest(4, 5, "  Muy bien  "));

        assertThat(usage.getAllyRating()).isEqualTo((short) 4);
        assertThat(usage.getProductRating()).isEqualTo((short) 5);
        assertThat(usage.getRatingComment()).isEqualTo("Muy bien");
        assertThat(usage.getRatedAt()).isNotNull();
        verify(usageRepository).refreshAllyRating(10L);
        verify(usageRepository).refreshAllyProductRating(20L);
        verify(usageRepository).refreshProductRating(30L);
    }

    @Test
    void rate_withoutOffer_onlyRefreshesTheAlly() {
        when(usageRepository.findByUuidForMemberUser(usageUuid, userUuid)).thenReturn(Optional.of(usage));

        service.rate(userUuid, usageUuid, new BenefitUsageRatingRequest(3, null, " "));

        assertThat(usage.getAllyRating()).isEqualTo((short) 3);
        assertThat(usage.getRatingComment()).isNull();
        verify(usageRepository).refreshAllyRating(10L);
        verify(usageRepository, never()).refreshAllyProductRating(anyLong());
        verify(usageRepository, never()).refreshProductRating(anyLong());
    }

    @Test
    void rate_productRatingWithoutOffer_isRejected() {
        when(usageRepository.findByUuidForMemberUser(usageUuid, userUuid)).thenReturn(Optional.of(usage));

        assertThatThrownBy(() -> service.rate(userUuid, usageUuid, new BenefitUsageRatingRequest(3, 4, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benefit_usage.rating.product_not_applicable");
    }

    @Test
    void rate_reversedUsage_isRejected() {
        usage.setStatus(UsageStatus.REVERSED.name());
        when(usageRepository.findByUuidForMemberUser(usageUuid, userUuid)).thenReturn(Optional.of(usage));

        assertThatThrownBy(() -> service.rate(userUuid, usageUuid, new BenefitUsageRatingRequest(5, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("benefit_usage.rating.not_ratable");
    }

    @Test
    void rate_someoneElsesUsage_isNotFound() {
        when(usageRepository.findByUuidForMemberUser(usageUuid, userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rate(userUuid, usageUuid, new BenefitUsageRatingRequest(5, null, null)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("benefit_usage.not_found");
    }
}
