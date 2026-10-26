package com.fenixcore.optibienestar360.modules.product.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.catalog.repository.ProductCategoryRepository;
import com.fenixcore.optibienestar360.modules.product.dto.ProductCreateRequest;
import com.fenixcore.optibienestar360.modules.product.dto.ProductDto;
import com.fenixcore.optibienestar360.modules.product.dto.ProductListItemDto;
import com.fenixcore.optibienestar360.modules.product.dto.ProductUpdateRequest;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import com.fenixcore.optibienestar360.modules.product.mapper.ProductMapper;
import com.fenixcore.optibienestar360.modules.product.repository.ProductRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD for the {@link Product} master (hub ADR 0026) plus the helpers
 * the ally-offer flows share to resolve or create the product an offer points
 * at — so the "general vs. exclusive" rule lives in one place.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductsService {

    private static final String ENTITY_KEY = "product";

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "code", "name", "productType", "requiresAppointment",
            "productCategory.uuid", "ownerAlly.uuid",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final String[] SEARCHABLE_FIELDS = {"name", "code", "description"};

    /** FK columns sort by their {@code _Display} alias (ADR 0014). */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Product.class, Map.of(
                    "productCategory_Display", "productCategory.name",
                    "ownerAlly_Display", "ownerAlly.name"
            ));

    private final ProductRepository repository;
    private final ProductCategoryRepository productCategoryRepository;
    private final AllyRepository allyRepository;
    private final ProductMapper mapper;
    private final DefaultSortResolver defaultSortResolver;

    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    // ─── Admin CRUD ──────────────────────────────────────────────────────────

    /**
     * Paginated, RSQL-filterable, free-text searchable list. {@code type}
     * narrows to services or products; {@code exclusive} to general
     * ({@code false}) or exclusive ({@code true}) products.
     */
    public Page<ProductListItemDto> list(Pageable pageable, String filter, String q, ProductType type,
                                         Boolean exclusive, boolean includeInactive) {
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(ENTITY_KEY, pageable);
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, ENTITY_KEY);

        Specification<Product> spec = includeInactive
                ? (root, query, cb) -> cb.conjunction()
                : (root, query, cb) -> cb.isTrue(root.get("active"));
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "product.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("productType"), type));
        }
        if (exclusive != null) {
            spec = spec.and((root, query, cb) -> exclusive
                    ? cb.isNotNull(root.get("ownerAlly"))
                    : cb.isNull(root.get("ownerAlly")));
        }
        return repository.findAll(spec, resolved).map(p -> mapper.toListItem(p, publicBaseUrl));
    }

    /** The sort {@link #list} actually applies — reported back to the admin table. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort(ENTITY_KEY, pageable);
    }

    public ProductDto get(UUID uuid) {
        return mapper.toDto(find(uuid), publicBaseUrl);
    }

    @Transactional
    @Auditable(entity = ENTITY_KEY, action = AuditAction.CREATE)
    public ProductDto create(ProductCreateRequest req) {
        Product product = new Product();
        product.setCode(uniqueCode(req.code(), null));
        product.setName(req.name().trim());
        product.setDescription(blankToNull(req.description()));
        product.setProductType(req.productType());
        product.setProductCategory(findCategory(req.productCategoryUuid()));
        product.setOwnerAlly(req.ownerAllyUuid() == null ? null : findAlly(req.ownerAllyUuid()));
        if (req.requiresAppointment() != null) {
            product.setRequiresAppointment(req.requiresAppointment());
        }
        return mapper.toDto(repository.save(product), publicBaseUrl);
    }

    @Transactional
    @Auditable(entity = ENTITY_KEY, action = AuditAction.UPDATE, uuidArgIndex = 0)
    public ProductDto update(UUID uuid, ProductUpdateRequest req) {
        Product product = find(uuid);
        if (req.code() != null)                product.setCode(uniqueCode(req.code(), product));
        if (req.name() != null)                product.setName(req.name().trim());
        if (req.description() != null)         product.setDescription(blankToNull(req.description()));
        if (req.productType() != null)         product.setProductType(req.productType());
        if (req.productCategoryUuid() != null) product.setProductCategory(findCategory(req.productCategoryUuid()));
        if (req.requiresAppointment() != null) product.setRequiresAppointment(req.requiresAppointment());
        if (req.active() != null)              product.setActive(req.active());
        if (Boolean.TRUE.equals(req.general())) {
            product.setOwnerAlly(null);
        } else if (req.ownerAllyUuid() != null) {
            // The V197 trigger refuses this while another ally still offers the product.
            product.setOwnerAlly(findAlly(req.ownerAllyUuid()));
        }
        return mapper.toDto(repository.saveAndFlush(product), publicBaseUrl);
    }

    /**
     * Soft delete. Offers of an inactive product drop out of the public
     * catalog on their own (the visibility rule requires an active product).
     */
    @Transactional
    @Auditable(entity = ENTITY_KEY, action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        find(uuid).setActive(false);
    }

    // ─── Helpers shared with the ally-offer flows ───────────────────────────

    /**
     * The product an offer of {@code ally} will point at: must exist, be
     * active and be offerable by that ally (general, or exclusive to it).
     */
    public Product requireOfferable(UUID productUuid, Ally ally) {
        Product product = repository.findByUuid(productUuid)
                .filter(Product::isActive)
                .orElseThrow(() -> new NoSuchElementException("product.not_found"));
        if (!product.isOfferableBy(ally)) {
            throw new IllegalArgumentException("ally_product.product_exclusive_to_other_ally");
        }
        return product;
    }

    /** Creates a product exclusive to {@code ally} — the path an offer takes when it brings its own item. */
    @Transactional
    public Product createExclusive(Ally ally, UUID productCategoryUuid, ProductType type, String name,
                                   String description, Boolean requiresAppointment) {
        if (productCategoryUuid == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("ally_product.product_required");
        }
        Product product = new Product();
        product.setOwnerAlly(ally);
        product.setProductCategory(findCategory(productCategoryUuid));
        product.setProductType(type != null ? type : ProductType.SERVICE);
        product.setName(name.trim());
        product.setDescription(blankToNull(description));
        product.setRequiresAppointment(Boolean.TRUE.equals(requiresAppointment));
        return repository.save(product);
    }

    /**
     * Applies item edits coming through an ally's offer. Only an exclusive
     * product of that same ally can be edited this way — a general product is
     * shared by other allies and is curated from the product master instead.
     */
    public void applyOfferEdits(Product product, Ally ally, UUID productCategoryUuid, ProductType type,
                                String name, String description, Boolean requiresAppointment) {
        boolean anyEdit = productCategoryUuid != null || type != null || name != null
                || description != null || requiresAppointment != null;
        if (!anyEdit) {
            return;
        }
        if (!product.isExclusive() || !product.isOfferableBy(ally)) {
            throw new IllegalArgumentException("ally_product.general_product_not_editable");
        }
        if (productCategoryUuid != null)  product.setProductCategory(findCategory(productCategoryUuid));
        if (type != null)                 product.setProductType(type);
        if (name != null && !name.isBlank()) product.setName(name.trim());
        if (description != null)          product.setDescription(blankToNull(description));
        if (requiresAppointment != null)  product.setRequiresAppointment(requiresAppointment);
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    private Product find(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("product.not_found"));
    }

    private ProductCategory findCategory(UUID uuid) {
        return productCategoryRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("product_category.not_found"));
    }

    private Ally findAlly(UUID uuid) {
        return allyRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    /** Blank clears the code; a code already used by another product is refused. */
    private String uniqueCode(String code, Product current) {
        String normalized = blankToNull(code);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase();
        boolean unchanged = current != null && normalized.equalsIgnoreCase(current.getCode());
        if (!unchanged && repository.existsByCodeIgnoreCase(normalized)) {
            throw new IllegalArgumentException("product.code.duplicate");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
