package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductOfferDto;
import com.fenixcore.optibienestar360.modules.media.MediaTables;
import com.fenixcore.optibienestar360.modules.media.dto.PublicImageDto;
import com.fenixcore.optibienestar360.modules.media.service.MediaImages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adds the galleries to the public DTOs (hub ADR 0027). MapStruct builds the
 * DTOs without {@code images}; this loads every needed gallery in two queries
 * (offers and products) and fills them in. <b>An offer without images of its
 * own shows its product's.</b>
 */
@Component
@RequiredArgsConstructor
public class PublicMediaEnricher {

    private final MediaImages media;

    /** An ally's public detail: its own gallery plus each offer's. */
    public PublicAllyDetailDto ally(PublicAllyDetailDto detail) {
        List<PublicAllyProductDto> products = allyProducts(detail.products());
        return new PublicAllyDetailDto(detail.uuid(), detail.name(), detail.allyTypeNames(), detail.allySegments(),
                detail.address(), detail.cityName(), detail.logoUrl(), detail.description(), detail.website(),
                detail.phone(), detail.whatsapp(), detail.instagram(), detail.facebook(), detail.googleMapsUrl(),
                detail.ratingAvg(), detail.ratingCount(), detail.professionNames(), products, products, media.forOwner(MediaTables.ALLIES, detail.uuid()));
    }

    /** Offers of one ally (its detail or {@code /allies/{uuid}/products}). */
    public List<PublicAllyProductDto> allyProducts(List<PublicAllyProductDto> offers) {
        if (offers == null || offers.isEmpty()) {
            return offers;
        }
        Map<UUID, List<PublicImageDto>> offerImages = media.forOwners(MediaTables.ALLY_PRODUCTS,
                offers.stream().map(PublicAllyProductDto::uuid).toList());
        Map<UUID, List<PublicImageDto>> productImages = media.forOwners(MediaTables.PRODUCTS,
                offers.stream().map(PublicAllyProductDto::productUuid).distinct().toList());
        return offers.stream()
                .map(o -> withImages(o, inherit(offerImages.get(o.uuid()), productImages.get(o.productUuid()))))
                .toList();
    }

    /** A product's public detail: its gallery plus each offering ally's offer gallery. */
    public PublicProductDetailDto product(PublicProductDetailDto detail) {
        List<PublicImageDto> productImages = media.forOwner(MediaTables.PRODUCTS, detail.uuid());
        Map<UUID, List<PublicImageDto>> offerImages = media.forOwners(MediaTables.ALLY_PRODUCTS,
                detail.offers().stream().map(PublicProductOfferDto::offerUuid).toList());
        List<PublicProductOfferDto> offers = detail.offers().stream()
                .map(o -> withImages(o, inherit(offerImages.get(o.offerUuid()), productImages)))
                .toList();
        return new PublicProductDetailDto(detail.uuid(), detail.name(), detail.description(), detail.productType(),
                detail.categoryName(), detail.requiresAppointment(), detail.imageUrl(),
                detail.ratingAvg(), detail.ratingCount(), detail.offersCount(),
                detail.minFinalPrice(), detail.maxDiscountPct(), offers, productImages);
    }

    // Records are immutable: copy with the gallery filled in.
    private static PublicAllyProductDto withImages(PublicAllyProductDto o, List<PublicImageDto> images) {
        return new PublicAllyProductDto(o.uuid(), o.productUuid(), o.productType(), o.inCatalog(), o.categoryName(),
                o.name(), o.description(), o.priceAmount(), o.discountPct(), o.finalPrice(), o.exclusions(),
                o.requiresAppointment(), o.ratingAvg(), o.ratingCount(), o.imageUrl(), images);
    }

    private static PublicProductOfferDto withImages(PublicProductOfferDto o, List<PublicImageDto> images) {
        return new PublicProductOfferDto(o.offerUuid(), o.priceAmount(), o.discountPct(), o.finalPrice(),
                o.exclusions(), o.ratingAvg(), o.ratingCount(), o.allyUuid(), o.allyName(), o.allyTypeNames(),
                o.allyCityName(), o.allyLogoUrl(), o.allyPhone(), o.allyWhatsapp(), o.allyRatingAvg(),
                o.allyRatingCount(), o.imageUrl(), images);
    }

    private static List<PublicImageDto> inherit(List<PublicImageDto> own, List<PublicImageDto> fallback) {
        if (own != null && !own.isEmpty()) {
            return own;
        }
        return fallback == null ? List.of() : fallback;
    }
}
