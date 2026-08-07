package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CollectionCommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.repository.CollectionCommissionTierRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD for {@link CollectionCommissionTier} (ADR 0013 §3, V44) — the
 * DB-driven collection-commission buckets the engine will read.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionCommissionTiersService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "maxDays", "commissionPct", "active", "status", "createdAt", "updatedAt"
    );

    private static final String[] SEARCHABLE_FIELDS = {"name"};

    private final CollectionCommissionTierRepository repository;

    public CollectionCommissionTierDto get(UUID uuid) {
        return CollectionCommissionTierDto.from(findManaged(uuid));
    }

    public Page<CollectionCommissionTierDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        Specification<CollectionCommissionTier> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "collection_commission_tier.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(CollectionCommissionTierDto::from);
    }

    @Transactional
    public CollectionCommissionTierDto create(CollectionCommissionTierCreateRequest req) {
        CollectionCommissionTier tier = new CollectionCommissionTier();
        tier.setName(req.name());
        tier.setMaxDays(req.maxDays());
        tier.setCommissionPct(req.commissionPct());

        return CollectionCommissionTierDto.from(repository.save(tier));
    }

    @Transactional
    public CollectionCommissionTierDto update(UUID uuid, CollectionCommissionTierUpdateRequest req) {
        CollectionCommissionTier tier = findManaged(uuid);

        if (req.name() != null)          tier.setName(req.name());
        if (req.maxDays() != null)       tier.setMaxDays(req.maxDays());
        if (req.commissionPct() != null) tier.setCommissionPct(req.commissionPct());
        if (req.active() != null)        tier.setActive(req.active());

        return CollectionCommissionTierDto.from(tier);   // managed → dirty-check on commit
    }

    @Transactional
    public void delete(UUID uuid) {
        findManaged(uuid).setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CollectionCommissionTier findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("collection_commission_tier.not_found"));
    }

    private static Specification<CollectionCommissionTier> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
