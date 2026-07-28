package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.CommissionTier;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionTierRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Admin CRUD for {@link CommissionTier} (v2 PDF #5, V42) — the DB-driven config
 * the commission engine reads. Plural name mirrors {@code PlansService}.
 *
 * <p>Enforces the pct-XOR-flat invariant the DB CHECK also guards, surfacing it
 * as a clean 422 ({@code commission_tier.pct_xor_flat}) instead of a constraint
 * violation.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionTiersService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "planType", "thresholdCount", "commissionPct", "flatAmount",
            "periodStrategy", "appliesTo", "active", "status", "createdAt", "updatedAt"
    );

    private static final String[] SEARCHABLE_FIELDS = {"name"};

    private final CommissionTierRepository repository;

    public CommissionTierDto get(UUID uuid) {
        return CommissionTierDto.from(findManaged(uuid));
    }

    public Page<CommissionTierDto> list(Pageable pageable, String filter, String q) {
        Specification<CommissionTier> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "commission_tier.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(CommissionTierDto::from);
    }

    @Transactional
    public CommissionTierDto create(CommissionTierCreateRequest req) {
        requireExactlyOneReward(req.commissionPct(), req.flatAmount());

        CommissionTier tier = new CommissionTier();
        tier.setName(req.name());
        tier.setPlanType(req.planType());
        tier.setThresholdCount(req.thresholdCount() != null ? req.thresholdCount() : 0);
        tier.setCommissionPct(req.commissionPct());
        tier.setFlatAmount(req.flatAmount());
        tier.setPeriodStrategy(req.periodStrategy());
        tier.setAppliesTo(req.appliesTo());

        return CommissionTierDto.from(repository.save(tier));
    }

    @Transactional
    public CommissionTierDto update(UUID uuid, CommissionTierUpdateRequest req) {
        CommissionTier tier = findManaged(uuid);

        if (req.name() != null)            tier.setName(req.name());
        if (req.planType() != null)        tier.setPlanType(req.planType());
        if (req.thresholdCount() != null)  tier.setThresholdCount(req.thresholdCount());
        if (req.periodStrategy() != null)  tier.setPeriodStrategy(req.periodStrategy());
        if (req.appliesTo() != null)       tier.setAppliesTo(req.appliesTo());
        if (req.active() != null)          tier.setActive(req.active());

        // Reward switch: supplying one clears the other (a tier is pct XOR flat).
        if (req.commissionPct() != null && req.flatAmount() != null) {
            throw new IllegalArgumentException("commission_tier.pct_xor_flat");
        }
        if (req.commissionPct() != null) {
            tier.setCommissionPct(req.commissionPct());
            tier.setFlatAmount(null);
        } else if (req.flatAmount() != null) {
            tier.setFlatAmount(req.flatAmount());
            tier.setCommissionPct(null);
        }
        requireExactlyOneReward(tier.getCommissionPct(), tier.getFlatAmount());

        return CommissionTierDto.from(tier);   // managed → dirty-check on commit
    }

    @Transactional
    public void delete(UUID uuid) {
        findManaged(uuid).setActive(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static void requireExactlyOneReward(BigDecimal pct, BigDecimal flat) {
        if ((pct == null) == (flat == null)) {
            throw new IllegalArgumentException("commission_tier.pct_xor_flat");
        }
    }

    private CommissionTier findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("commission_tier.not_found"));
    }

    private static Specification<CommissionTier> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
