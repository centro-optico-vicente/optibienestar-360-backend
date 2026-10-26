package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import com.fenixcore.optibienestar360.modules.product.repository.ProductRepository;
import com.fenixcore.optibienestar360.modules.product.service.ProductsService;
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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Admin-side CRUD for the {@link AllyProduct} offers of a parent {@link Ally}
 * (hub ADR 0026). Workflow transitions (approve / reject / remove) live in
 * {@link AllyProductReviewService} behind {@code /v1/admin/ally-products/{uuid}/...};
 * this one only deals with create / read / update / soft-delete.
 *
 * <p>An offer points at a general product, or at one exclusive to the ally —
 * created on the fly when the request brings its own item fields. New rows
 * land at {@code reviewStatus = PROPOSED} (V11 column default); the admin
 * moves them to APPROVED via the workflow endpoint so the review log captures
 * the actor + reason.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyProductsAdminService {

    private static final String ENTITY_KEY = "ally_product";

    /** Product fields live on the master — sorted through the association. */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(AllyProduct.class, Map.of(
                    "name", "product.name",
                    "productType", "product.productType",
                    "productCategory", "product.productCategory.name"
            ));

    private static final String[] SEARCHABLE_FIELDS = {"product.name", "product.description"};

    private final AllyRepository allyRepository;
    private final AllyProductRepository offerRepository;
    private final CurrencyRepository currencyRepository;
    private final ProductsService productsService;
    private final ProductRepository productRepository;
    private final AllyMapper mapper;
    private final DefaultSortResolver defaultSortResolver;

    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    /** The ally's active offers, paginated; optional {@code type} and free-text {@code q} over the product. */
    public Page<AllyProductDto> listForAlly(UUID allyUuid, ProductType type, String q, Pageable pageable) {
        Ally ally = findAlly(allyUuid);
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(ENTITY_KEY, pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, ENTITY_KEY);

        Specification<AllyProduct> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("ally").get("id"), ally.getId()),
                cb.isTrue(root.get("active")));
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("product").get("productType"), type));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return offerRepository.findAll(spec, resolved).map(offer -> mapper.toAllyProductDto(offer, publicBaseUrl));
    }

    /**
     * Every ally's offer of one product — the internal side of the public
     * comparison: all review states, published or not (only soft-deleted
     * offers are left out). Paginated; default order is the ally's name.
     */
    public Page<AllyProductDto> listForProduct(UUID productUuid, Pageable pageable) {
        Product product = productRepository.findByUuid(productUuid)
                .orElseThrow(() -> new NoSuchElementException("product.not_found"));
        Pageable resolved = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("ally.name"));
        Specification<AllyProduct> spec = (root, query, cb) -> cb.and(
                cb.equal(root.get("product").get("id"), product.getId()),
                cb.isTrue(root.get("active")));
        return offerRepository.findAll(spec, resolved).map(offer -> mapper.toAllyProductDto(offer, publicBaseUrl));
    }

    public AllyProductDto get(UUID allyUuid, UUID offerUuid) {
        return mapper.toAllyProductDto(findOfferUnderAlly(allyUuid, offerUuid), publicBaseUrl);
    }

    @Transactional
    @Auditable(entity = ENTITY_KEY, action = AuditAction.CREATE)
    public AllyProductDto create(UUID allyUuid, AllyProductCreateRequest req) {
        Ally ally = findAlly(allyUuid);
        Product product = req.productUuid() != null
                ? productsService.requireOfferable(req.productUuid(), ally)
                : productsService.createExclusive(ally, req.productCategoryUuid(), req.productType(),
                        req.name(), req.description(), req.requiresAppointment());
        requireNotOffered(ally, product);

        AllyProduct offer = new AllyProduct();
        offer.setAlly(ally);
        offer.setProduct(product);
        applyPrice(offer, req.priceAmount());
        offer.setDiscountPct(req.discountPct());
        offer.setExclusions(blankToNull(req.exclusions()));
        // reviewStatus stays at PROPOSED default — admin moves it to APPROVED
        // via the workflow endpoint to log the actor + reason.
        return mapper.toAllyProductDto(offerRepository.save(offer), publicBaseUrl);
    }

    @Transactional
    @Auditable(entity = ENTITY_KEY, action = AuditAction.UPDATE, uuidArgIndex = 1)
    public AllyProductDto update(UUID allyUuid, UUID offerUuid, AllyProductUpdateRequest req) {
        AllyProduct offer = findOfferUnderAlly(allyUuid, offerUuid);

        productsService.applyOfferEdits(offer.getProduct(), offer.getAlly(), req.productCategoryUuid(),
                req.productType(), req.name(), req.description(), req.requiresAppointment());

        if (req.priceAmount() != null)  applyPrice(offer, req.priceAmount());
        if (req.discountPct() != null)  offer.setDiscountPct(req.discountPct());
        // exclusions: null = unchanged, blank = cleared.
        if (req.exclusions() != null)   offer.setExclusions(blankToNull(req.exclusions()));
        if (req.published() != null)    offer.setPublished(req.published());
        if (req.active() != null)       offer.setActive(req.active());

        return mapper.toAllyProductDto(offer, publicBaseUrl);  // managed → dirty-check on commit
    }

    @Transactional
    @Auditable(entity = ENTITY_KEY, action = AuditAction.DELETE, uuidArgIndex = 1)
    public void delete(UUID allyUuid, UUID offerUuid) {
        AllyProduct offer = findOfferUnderAlly(allyUuid, offerUuid);
        offer.setActive(false);
        offer.setPublished(false);  // auto-unpublish, same as ally delete
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Reference prices are USD-only today (ADR 0015 follow-up, V91). */
    private void applyPrice(AllyProduct offer, BigDecimal amount) {
        offer.setPriceAmount(amount);
        offer.setPriceCurrency(amount != null ? usdCurrency() : null);
    }

    private Currency usdCurrency() {
        return currencyRepository.findByCode("USD")
                .orElseThrow(() -> new IllegalStateException("currency.usd_not_seeded"));
    }

    /** One offer per (ally, product) — {@code uq_ally_products_ally_product}, answered with a readable error. */
    private void requireNotOffered(Ally ally, Product product) {
        if (product.getId() != null && offerRepository.existsByAllyIdAndProductId(ally.getId(), product.getId())) {
            throw new IllegalArgumentException("ally_product.already_offered");
        }
    }

    private Ally findAlly(UUID uuid) {
        return allyRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    /**
     * Resolves the offer AND verifies it belongs to the parent ally — guards
     * against URL tampering ({@code /allies/A/products/X} where X belongs to
     * ally B should 404, not silently operate on the wrong ally).
     */
    private AllyProduct findOfferUnderAlly(UUID allyUuid, UUID offerUuid) {
        AllyProduct offer = offerRepository.findByUuid(offerUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_product.not_found"));
        if (!offer.getAlly().getUuid().equals(allyUuid)) {
            throw new NoSuchElementException("ally_product.not_found");
        }
        return offer;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
