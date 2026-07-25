package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicServiceListItemDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Anonymous read surface for ally offerings, in two shapes:
 *
 * <ul>
 *   <li>{@link #search} — the <b>cross-ally</b> catalog behind
 *       {@code GET /v1/public/services} ("who offers X?"), returning the
 *       richer {@link PublicServiceListItemDto} that carries each service's
 *       parent-ally identity.</li>
 *   <li>{@link #listByAlly} — <b>one ally's</b> offerings behind
 *       {@code GET /v1/public/allies/{uuid}/services}, a dedicated paginated /
 *       filterable list (the same data the ally detail nests, but standalone)
 *       returning the leaner {@link PublicAllyServiceDto}.</li>
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
public class PublicServicesService {

    private final AllyServiceRepository serviceRepository;
    private final AllyRepository allyRepository;
    private final AllyMapper mapper;

    /**
     * Cross-ally public catalog. Optional filters combine with AND:
     * {@code categoryUuid} (service category), {@code cityUuid} (parent ally's
     * city), {@code q} (accent-insensitive free-text over service name +
     * description).
     */
    public Page<PublicServiceListItemDto> search(UUID categoryUuid, UUID cityUuid,
                                                 String q, Pageable pageable) {
        Specification<AllyService> spec = publiclyVisible();
        if (categoryUuid != null) {
            spec = spec.and(categoryEquals(categoryUuid));
        }
        if (cityUuid != null) {
            spec = spec.and(allyCityEquals(cityUuid));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, "name", "description"));
        }
        return serviceRepository.findAll(spec, pageable).map(mapper::toPublicServiceListItem);
    }

    /**
     * One ally's public offerings. 404s ({@code ally.not_found}) when the ally
     * doesn't exist OR is not publicly visible — an anonymous caller can't probe
     * an unpublished ally's services. Optional {@code categoryUuid} / {@code q}
     * narrow the list.
     */
    public Page<PublicAllyServiceDto> listByAlly(UUID allyUuid, UUID categoryUuid,
                                                 String q, Pageable pageable) {
        Ally ally = allyRepository.findByUuid(allyUuid)
                .filter(Ally::isActive)
                .filter(Ally::isPublished)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));

        Specification<AllyService> spec = publiclyVisible()
                .and((root, query, cb) -> cb.equal(root.get("ally").get("id"), ally.getId()));
        if (categoryUuid != null) {
            spec = spec.and(categoryEquals(categoryUuid));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, "name", "description"));
        }
        return serviceRepository.findAll(spec, pageable).map(mapper::toPublicServiceDto);
    }

    // ─── Specs ────────────────────────────────────────────────────────────────

    /** Service + parent ally both cleared for anonymous consumption. */
    private static Specification<AllyService> publiclyVisible() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("active")),
                cb.isTrue(root.get("published")),
                cb.equal(root.get("reviewStatus"), AllyService.ReviewStatus.APPROVED),
                cb.isTrue(root.get("ally").get("active")),
                cb.isTrue(root.get("ally").get("published"))
        );
    }

    private static Specification<AllyService> categoryEquals(UUID categoryUuid) {
        return (root, query, cb) -> cb.equal(root.get("serviceCategory").get("uuid"), categoryUuid);
    }

    private static Specification<AllyService> allyCityEquals(UUID cityUuid) {
        return (root, query, cb) -> cb.equal(root.get("ally").get("city").get("uuid"), cityUuid);
    }
}
