package com.fenixcore.optisaludplus.modules.promoter.service;

import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.promoter.dto.CommissionDto;
import com.fenixcore.optisaludplus.modules.promoter.entity.Commission;
import com.fenixcore.optisaludplus.modules.promoter.mapper.CommissionMapper;
import com.fenixcore.optisaludplus.modules.promoter.repository.CommissionRepository;
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
 * Admin-side reads over {@link Commission}. Plural-name convention matches
 * {@code PlansService}, {@code PaymentsService}, {@code PromotersService}.
 * Distinct from the singular {@link CommissionService} that owns the
 * calc-and-persist operation: this one is for the admin queue
 * (list + single + RSQL + free-text); the singular keeps its narrow role
 * as the attribution engine wired into {@code PaymentsService.approve}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommissionsService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "status", "appliesTo", "periodStrategy", "currency",
            "amount", "commissionPct", "flatAmount", "calculationBasis",
            "periodStart", "periodEnd", "earnedAt",
            "paidAt", "voidedAt",
            "createdAt", "updatedAt", "active"
    );

    private static final String[] SEARCHABLE_FIELDS = {
            "payoutReference", "adminNotes", "tierNameSnapshot", "voidReason"
    };

    private final CommissionRepository repository;
    private final CommissionMapper mapper;

    // ─── Read ───────────────────────────────────────────────────────────────

    public CommissionDto get(UUID uuid) {
        return mapper.toDto(findManaged(uuid));
    }

    /**
     * Admin queue list. Default sort {@code earnedAt DESC} matches the V26
     * composite index {@code idx_commissions_member_earned} when filtered
     * by member; the {@code (promoter_id, status, period_start)} index
     * backs filters by promoter + status + period (the canonical
     * liquidation path).
     */
    public Page<CommissionDto> list(Pageable pageable, String filter, String q) {
        Specification<Commission> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "commission.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(mapper::toDto);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Commission findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("commission.not_found"));
    }

    private static Specification<Commission> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
