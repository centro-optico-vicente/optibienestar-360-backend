package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.ServiceCategory;
import com.fenixcore.optibienestar360.modules.catalog.repository.ServiceCategoryRepository;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Admin-side CRUD for the {@link AllyService} entity scoped to a parent
 * {@link Ally}. Workflow transitions (approve / reject / remove) live in a
 * separate service backing the {@code /v1/admin/ally-services/{uuid}/...}
 * endpoints; this one only deals with create / read / update / soft-delete.
 *
 * <p>New rows land at {@code reviewStatus = PROPOSED} by default — the V11
 * column default. Admin moves them to APPROVED via the workflow endpoint
 * so the {@code ally_service_review_log} captures the actor + reason.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyServicesAdminService {

    /** {@code serviceCategory} is nested (not a flat {@code _Display} field) — sorted by its own catalog name. */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(AllyService.class, Map.of(
                    "serviceCategory", "serviceCategory.name"
            ));

    private final AllyRepository allyRepository;
    private final AllyServiceRepository serviceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final AllyMapper mapper;
    private final DefaultSortResolver defaultSortResolver;

    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    public List<AllyServiceDto> listForAlly(UUID allyUuid, Pageable pageable) {
        Ally ally = findAlly(allyUuid);
        Pageable defaulted = defaultSortResolver.withDefaultSortIfUnsorted(
                "ally_service", pageable, new SortOrder("name", "ASC"));
        Pageable resolved = SortFieldValidator.resolve(defaulted, SORTABLE_FIELDS, "ally_service");
        return serviceRepository.findByAllyIdAndActiveTrue(ally.getId(), resolved.getSort()).stream()
                .map(service -> mapper.toServiceDto(service, publicBaseUrl))
                .toList();
    }

    public AllyServiceDto get(UUID allyUuid, UUID serviceUuid) {
        AllyService service = findServiceUnderAlly(allyUuid, serviceUuid);
        return mapper.toServiceDto(service, publicBaseUrl);
    }

    @Transactional
    @Auditable(entity = "ally_service", action = AuditAction.CREATE)
    public AllyServiceDto create(UUID allyUuid, AllyServiceCreateRequest req) {
        Ally ally = findAlly(allyUuid);
        ServiceCategory category = serviceCategoryRepository.findByUuid(req.serviceCategoryUuid())
                .orElseThrow(() -> new NoSuchElementException("service_category.not_found"));

        AllyService service = new AllyService();
        service.setAlly(ally);
        service.setServiceCategory(category);
        service.setName(req.name());
        service.setDescription(req.description());
        service.setPriceUsd(req.priceUsd());
        service.setDiscountPct(req.discountPct());
        if (req.requiresAppointment() != null) {
            service.setRequiresAppointment(req.requiresAppointment());
        }
        // reviewStatus stays at PROPOSED default — admin moves it to APPROVED
        // via the workflow endpoint to log the actor + reason.
        return mapper.toServiceDto(serviceRepository.save(service), publicBaseUrl);
    }

    @Transactional
    @Auditable(entity = "ally_service", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public AllyServiceDto update(UUID allyUuid, UUID serviceUuid, AllyServiceUpdateRequest req) {
        AllyService service = findServiceUnderAlly(allyUuid, serviceUuid);

        if (req.serviceCategoryUuid() != null) {
            ServiceCategory category = serviceCategoryRepository.findByUuid(req.serviceCategoryUuid())
                    .orElseThrow(() -> new NoSuchElementException("service_category.not_found"));
            service.setServiceCategory(category);
        }
        if (req.name() != null)                service.setName(req.name());
        if (req.description() != null)         service.setDescription(req.description());
        if (req.priceUsd() != null)            service.setPriceUsd(req.priceUsd());
        if (req.discountPct() != null)         service.setDiscountPct(req.discountPct());
        if (req.requiresAppointment() != null) service.setRequiresAppointment(req.requiresAppointment());
        if (req.published() != null)           service.setPublished(req.published());
        if (req.active() != null)              service.setActive(req.active());

        return mapper.toServiceDto(service, publicBaseUrl);  // managed → dirty-check on commit
    }

    @Transactional
    @Auditable(entity = "ally_service", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void delete(UUID allyUuid, UUID serviceUuid) {
        AllyService service = findServiceUnderAlly(allyUuid, serviceUuid);
        service.setActive(false);
        service.setPublished(false);  // auto-unpublish, same as ally delete
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Ally findAlly(UUID uuid) {
        return allyRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    /**
     * Resolves the service AND verifies it belongs to the parent ally — guards
     * against URL tampering ({@code /allies/A/services/X} where X belongs to
     * ally B should 404, not silently operate on the wrong ally).
     */
    private AllyService findServiceUnderAlly(UUID allyUuid, UUID serviceUuid) {
        AllyService service = serviceRepository.findByUuid(serviceUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_service.not_found"));
        if (!service.getAlly().getUuid().equals(allyUuid)) {
            throw new NoSuchElementException("ally_service.not_found");
        }
        return service;
    }
}
