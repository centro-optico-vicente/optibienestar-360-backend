package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductListItemDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Anonymous read surface for ally offerings, in two shapes:
 *
 * <ul>
 *   <li>{@link #search} — the <b>cross-ally</b> catalog behind
 *       {@code GET /v1/public/services} ("who offers X?"), returning the
 *       richer {@link PublicProductListItemDto} that carries each service's
 *       parent-ally identity.</li>
 *   <li>{@link #listByAlly} — <b>one ally's</b> offerings behind
 *       {@code GET /v1/public/allies/{uuid}/services}, a dedicated paginated /
 *       filterable list (the same data the ally detail nests, but standalone)
 *       returning the leaner {@link PublicAllyProductDto}.</li>
 * </ul>
 *
 * <p><b>Visibility invariant (both paths):</b> a service is public only when it
 * is {@code active AND published AND reviewStatus=APPROVED} <i>and</i> its
 * parent ally is itself {@code active AND published}. An unpublished ally's
 * offerings never surface even if the service row is published — mirrors the
 * {@code PublicAllyController} contract. Filters ({@code categoryUuid},
 * {@code cityUuid}, {@code q}) are best-effort: an unknown UUID simply yields
 * an empty page rather than a 404, matching {@code AlliesService.publicDirectory}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicProductsService {

    /** Free-text {@code ?q=} fields — the offered product's name/description. */
    private static final String[] SEARCHABLE_FIELDS = {"product.name", "product.description"};

    /** Sortable properties that live on the product master, not on the offer. */
    private static final Set<String> PRODUCT_SORT_FIELDS = Set.of("name", "productType", "code");

    private final AllyProductRepository serviceRepository;
    private final AllyRepository allyRepository;
    private final AllyMapper mapper;

    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    /**
     * Cross-ally public catalog. Optional filters combine with AND:
     * {@code categoryUuid} (service category), {@code cityUuid} (parent ally's
     * city), {@code q} (accent-insensitive free-text over service name +
     * description).
     */
    public Page<PublicProductListItemDto> search(UUID categoryUuid, UUID cityUuid,
                                                 String q, Pageable pageable) {
        Specification<AllyProduct> spec = publiclyVisible();
        if (categoryUuid != null) {
            spec = spec.and(categoryEquals(categoryUuid));
        }
        if (cityUuid != null) {
            spec = spec.and(allyCityEquals(cityUuid));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return serviceRepository.findAll(spec, productSort(pageable))
                .map(offer -> mapper.toPublicProductListItem(offer, publicBaseUrl));
    }

    /**
     * One ally's public offerings. 404s ({@code ally.not_found}) when the ally
     * doesn't exist OR is not publicly visible — an anonymous caller can't probe
     * an unpublished ally's services. Optional {@code categoryUuid} / {@code q}
     * narrow the list.
     */
    public Page<PublicAllyProductDto> listByAlly(UUID allyUuid, UUID categoryUuid,
                                                 String q, Pageable pageable) {
        Ally ally = allyRepository.findByUuid(allyUuid)
                .filter(Ally::isActive)
                .filter(Ally::isPublished)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));

        Specification<AllyProduct> spec = publiclyVisible()
                .and((root, query, cb) -> cb.equal(root.get("ally").get("id"), ally.getId()));
        if (categoryUuid != null) {
            spec = spec.and(categoryEquals(categoryUuid));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return serviceRepository.findAll(spec, productSort(pageable))
                .map(offer -> mapper.toPublicProductDto(offer, publicBaseUrl));
    }

    // ─── Specs ────────────────────────────────────────────────────────────────

    /** Service + parent ally both cleared for anonymous consumption. */
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

    private static Specification<AllyProduct> allyCityEquals(UUID cityUuid) {
        return (root, query, cb) -> cb.equal(root.get("ally").get("city").get("uuid"), cityUuid);
    }
}
