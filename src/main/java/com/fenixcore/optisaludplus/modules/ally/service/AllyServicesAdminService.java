package com.fenixcore.optisaludplus.modules.ally.service;

import com.fenixcore.optisaludplus.modules.ally.dto.AllyServiceCreateRequest;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyServiceUpdateRequest;
import com.fenixcore.optisaludplus.modules.ally.entity.Ally;
import com.fenixcore.optisaludplus.modules.ally.entity.AllyService;
import com.fenixcore.optisaludplus.modules.ally.mapper.AllyMapper;
import com.fenixcore.optisaludplus.modules.ally.repository.AllyRepository;
import com.fenixcore.optisaludplus.modules.ally.repository.AllyServiceRepository;
import com.fenixcore.optisaludplus.modules.catalog.entity.ServiceCategory;
import com.fenixcore.optisaludplus.modules.catalog.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    private final AllyRepository allyRepository;
    private final AllyServiceRepository serviceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final AllyMapper mapper;

    public List<AllyServiceDto> listForAlly(UUID allyUuid) {
        Ally ally = findAlly(allyUuid);
        return serviceRepository.findByAllyIdAndActiveTrue(ally.getId()).stream()
                .map(mapper::toServiceDto)
                .toList();
    }

    public AllyServiceDto get(UUID allyUuid, UUID serviceUuid) {
        AllyService service = findServiceUnderAlly(allyUuid, serviceUuid);
        return mapper.toServiceDto(service);
    }

    @Transactional
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
        return mapper.toServiceDto(serviceRepository.save(service));
    }

    @Transactional
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

        return mapper.toServiceDto(service);  // managed → dirty-check on commit
    }

    @Transactional
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
