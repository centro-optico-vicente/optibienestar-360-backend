package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.ProposeAllyServiceRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.ServiceCategory;
import com.fenixcore.optibienestar360.modules.catalog.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Ally-side proposal flow for {@link AllyService}. Backs the
 * {@code POST /v1/aliado/services} endpoint.
 *
 * <p>Authorization is membership-based, NOT just permission-based: any
 * authenticated user can hit the endpoint, but the service rejects with
 * 403 unless the caller has an <b>active</b> {@link AllyUser} membership
 * on the target ally with {@code allyRole} in (OWNER, STAFF). VIEWER is
 * intentionally excluded — read-only roles can't propose. This combines
 * with the broader RBAC: callers should also have the global
 * {@code ALIADO} role, but we don't double-guard at the permission level
 * because the membership check is the load-bearing rule here.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyServicesProposeService {

    private final AllyUserRepository allyUserRepository;
    private final AllyServiceRepository serviceRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final AllyMapper mapper;

    /**
     * Create a new {@link AllyService} on behalf of the actor under the
     * requested ally. The row lands at {@code reviewStatus=PROPOSED} —
     * admin moves it via the workflow endpoints so the audit log captures
     * the transition.
     *
     * @param actorUserUuid the JWT subject (user UUID).
     * @param req payload — {@code allyUuid} chooses which ally to propose
     *            for.
     * @return the persisted service mapped to {@link AllyServiceDto}.
     * @throws AccessDeniedException if the actor has no active OWNER/STAFF
     *         membership on the target ally.
     * @throws NoSuchElementException if the service category UUID doesn't
     *         resolve.
     */
    @Transactional
    public AllyServiceDto propose(UUID actorUserUuid, ProposeAllyServiceRequest req) {
        AllyUser membership = allyUserRepository
                .findActiveByAllyUuidAndUserUuid(req.allyUuid(), actorUserUuid)
                .orElseThrow(() -> new AccessDeniedException("ally_service.propose.not_allowed"));

        if (membership.getAllyRole() == AllyRole.VIEWER) {
            // VIEWER can read but never propose.
            throw new AccessDeniedException("ally_service.propose.not_allowed");
        }

        ServiceCategory category = serviceCategoryRepository.findByUuid(req.serviceCategoryUuid())
                .orElseThrow(() -> new NoSuchElementException("service_category.not_found"));

        AllyService service = new AllyService();
        service.setAlly(membership.getAlly());  // reuse the already-loaded managed ally
        service.setServiceCategory(category);
        service.setName(req.name());
        service.setDescription(req.description());
        service.setPriceUsd(req.priceUsd());
        service.setDiscountPct(req.discountPct());
        if (req.requiresAppointment() != null) {
            service.setRequiresAppointment(req.requiresAppointment());
        }
        // reviewStatus stays at PROPOSED (V11 column default). reviewedBy /
        // reviewedAt remain null until an admin moves it to APPROVED /
        // REJECTED via the workflow endpoints.

        return mapper.toServiceDto(serviceRepository.save(service));
    }
}
