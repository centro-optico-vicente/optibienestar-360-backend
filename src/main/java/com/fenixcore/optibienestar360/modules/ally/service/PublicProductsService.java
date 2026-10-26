package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductOfferDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import com.fenixcore.optibienestar360.modules.product.mapper.ProductMapper;
import com.fenixcore.optibienestar360.modules.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Anonymous read surface for the product catalog (hub ADR 0026), in three shapes:
 *
 * <ul>
 *   <li>{@link #search} — the <b>cross-ally</b> catalog behind
 *       {@code GET /v1/public/products}: one row per visible offer, with the
 *       product and a slice of the parent ally's identity.</li>
 *   <li>{@link #listByAlly} — <b>one ally's</b> offers behind
 *       {@code GET /v1/public/allies/{uuid}/products}.</li>
 *   <li>{@link #productDetail} — <b>one product</b> with every ally that offers
 *       it and its own price and discount, behind {@code GET /v1/public/products/{uuid}}.</li>
 * </ul>
 *
 * <p><b>Visibility invariant (every path):</b> an offer is public only when it
 * is {@code active AND published AND reviewStatus=APPROVED}, its product is
 * active, <i>and</i> its parent ally is itself {@code active AND published}.
 * List filters are best-effort: an unknown UUID yields an empty page rather
 * than a 404, matching {@code AlliesService.publicDirectory}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicProductsService {

    /** Free-text {@code ?q=} fields inside one ally — the offered product's name, description and category. */
    private static final String[] SEARCHABLE_FIELDS = {"product.name", "product.description", "product.productCategory.name"};

    /** Sortable properties that live on the product master, not on the offer. */
    private static final Set<String> PRODUCT_SORT_FIELDS = Set.of("name", "productType", "code");

    /**
     * Comparison order of a product's offers: best final price first (offers
     * without a price last), then higher discount, then ally name.
     */
    private static final Comparator<AllyProduct> BEST_OFFER_FIRST =
            Comparator.comparing(AllyProduct::getFinalPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(AllyProduct::getDiscountPct, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(offer -> offer.getAlly().getName(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final AllyProductRepository offerRepository;
    private final AllyRepository allyRepository;
    private final ProductRepository productRepository;
    private final AllyMapper mapper;

    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    /**
     * Cross-ally public catalog. Every filter of {@link PublicProductSearch} is
     * optional and they combine with AND; text filters take plain words
     * (city, ally name / RIF / number, category name, "servicio"/"producto").
     */
    public Page<PublicProductListItemDto> search(PublicProductSearch search, Pageable pageable) {
        Specification<AllyProduct> spec = publiclyVisible();
        if (search.categoryUuid() != null) {
            spec = spec.and(categoryEquals(search.categoryUuid()));
        }
        if (PublicCatalogSearch.hasText(search.category())) {
            spec = spec.and(PublicCatalogSearch.offerCategoryMatches(search.category()));
        }
        if (search.cityUuid() != null) {
            spec = spec.and(allyCityEquals(search.cityUuid()));
        }
        if (PublicCatalogSearch.hasText(search.city())) {
            spec = spec.and(PublicCatalogSearch.offerCityMatches(search.city()));
        }
        if (search.allyUuid() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ally").get("uuid"), search.allyUuid()));
        }
        if (PublicCatalogSearch.hasText(search.ally())) {
            spec = spec.and(PublicCatalogSearch.offerAllyMatches(search.ally()));
        }
        ProductType type = PublicCatalogSearch.parseType(search.type());
        if (type != null) {
            spec = spec.and(typeEquals(type));
        }
        if (PublicCatalogSearch.hasText(search.q())) {
            spec = spec.and(PublicCatalogSearch.offerMatchesAnything(search.q()));
        }
        return offerRepository.findAll(spec, productSort(pageable))
                .map(offer -> mapper.toPublicProductListItem(offer, publicBaseUrl));
    }

    /**
     * One ally's public offers. 404s ({@code ally.not_found}) when the ally
     * doesn't exist OR is not publicly visible — an anonymous caller can't probe
     * an unpublished ally's offers. Optional {@code categoryUuid} / {@code category}
     * (name or code) / {@code type} ("servicio"/"producto") / {@code q} narrow the list.
     */
    public Page<PublicAllyProductDto> listByAlly(UUID allyUuid, UUID categoryUuid, String category,
                                                 String type, String q, Pageable pageable) {
        Ally ally = allyRepository.findByUuid(allyUuid)
                .filter(Ally::isActive)
                .filter(Ally::isPublished)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));

        Specification<AllyProduct> spec = publiclyVisible()
                .and((root, query, cb) -> cb.equal(root.get("ally").get("id"), ally.getId()));
        if (categoryUuid != null) {
            spec = spec.and(categoryEquals(categoryUuid));
        }
        if (PublicCatalogSearch.hasText(category)) {
            spec = spec.and(PublicCatalogSearch.offerCategoryMatches(category));
        }
        ProductType productType = PublicCatalogSearch.parseType(type);
        if (productType != null) {
            spec = spec.and(typeEquals(productType));
        }
        if (PublicCatalogSearch.hasText(q)) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return offerRepository.findAll(spec, productSort(pageable))
                .map(offer -> mapper.toPublicProductDto(offer, publicBaseUrl));
    }

    /**
     * One product with every ally that offers it publicly — the price /
     * discount comparison. Optional {@code cityUuid} or {@code city} (city or
     * state name) keeps the allies of one place. 404s ({@code product.not_found}) when the product doesn't exist, is
     * inactive or has no visible offer (after the city filter), so anonymous
     * callers never discover items nobody offers.
     */
    public PublicProductDetailDto productDetail(UUID productUuid, UUID cityUuid, String city) {
        Product product = productRepository.findByUuid(productUuid)
                .filter(Product::isActive)
                .orElseThrow(() -> new NoSuchElementException("product.not_found"));

        Specification<AllyProduct> spec = publiclyVisible()
                .and((root, query, cb) -> cb.equal(root.get("product").get("id"), product.getId()));
        if (cityUuid != null) {
            spec = spec.and(allyCityEquals(cityUuid));
        }
        if (PublicCatalogSearch.hasText(city)) {
            spec = spec.and(PublicCatalogSearch.offerCityMatches(city));
        }
        List<AllyProduct> offers = offerRepository.findAll(spec).stream()
                .sorted(BEST_OFFER_FIRST)
                .toList();
        if (offers.isEmpty()) {
            throw new NoSuchElementException("product.not_found");
        }

        List<PublicProductOfferDto> offerDtos = offers.stream().map(mapper::toPublicProductOffer).toList();
        BigDecimal minFinalPrice = offers.stream().map(AllyProduct::getFinalPrice)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        BigDecimal maxDiscountPct = offers.stream().map(AllyProduct::getDiscountPct)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);

        return new PublicProductDetailDto(
                product.getUuid(),
                product.getName(),
                product.getDescription(),
                product.getProductType(),
                product.getProductCategory() == null ? null : product.getProductCategory().getName(),
                product.isRequiresAppointment(),
                ProductMapper.imageUrl(product, publicBaseUrl),
                offerDtos.size(),
                minFinalPrice,
                maxDiscountPct,
                offerDtos);
    }

    // ─── Specs ────────────────────────────────────────────────────────────────

    /** Offer, its product and its parent ally all cleared for anonymous consumption. */
    private static Specification<AllyProduct> publiclyVisible() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("active")),
                cb.isTrue(root.get("published")),
                cb.equal(root.get("reviewStatus"), AllyProduct.ReviewStatus.APPROVED),
                cb.isTrue(root.get("product").get("active")),
                cb.isTrue(root.get("ally").get("active")),
                cb.isTrue(root.get("ally").get("published"))
        );
    }

    /**
     * Product fields (name, type…) live on the master since ADR 0026, so a
     * client sort on them is re-pointed through the association; the
     * offer's own fields (priceAmount, discountPct…) pass through unchanged.
     */
    private static Pageable productSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort sort = Sort.by(pageable.getSort().stream()
                .map(order -> PRODUCT_SORT_FIELDS.contains(order.getProperty())
                        ? order.withProperty("product." + order.getProperty())
                        : order)
                .toList());
        return pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                : Pageable.unpaged(sort);
    }

    private static Specification<AllyProduct> categoryEquals(UUID categoryUuid) {
        return (root, query, cb) -> cb.equal(root.get("product").get("productCategory").get("uuid"), categoryUuid);
    }

    private static Specification<AllyProduct> typeEquals(ProductType type) {
        return (root, query, cb) -> cb.equal(root.get("product").get("productType"), type);
    }

    private static Specification<AllyProduct> allyCityEquals(UUID cityUuid) {
        return (root, query, cb) -> cb.equal(root.get("ally").get("city").get("uuid"), cityUuid);
    }
}
