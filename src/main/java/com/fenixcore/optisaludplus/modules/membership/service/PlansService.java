package com.fenixcore.optisaludplus.modules.membership.service;

import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.membership.dto.PlanCreateRequest;
import com.fenixcore.optisaludplus.modules.membership.dto.PlanDto;
import com.fenixcore.optisaludplus.modules.membership.dto.PlanUpdateRequest;
import com.fenixcore.optisaludplus.modules.membership.entity.Plan;
import com.fenixcore.optisaludplus.modules.membership.mapper.PlanMapper;
import com.fenixcore.optisaludplus.modules.membership.repository.PlanRepository;
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
 * Application service for {@link Plan} — admin CRUD + RSQL list.
 *
 * <p>Plural class name ({@code PlansService}) to mirror the convention used
 * by {@code AlliesService} / {@code MembersService} and to leave the
 * singular {@code PlanService} name free for a possible future business
 * service around a Plan transaction (analogous to how {@code AllyService}
 * the entity coexists with {@code AlliesService} the manager).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlansService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "code", "name", "type",
            "inscriptionFee", "monthlyFee",
            "includedBeneficiaries", "maxBeneficiaries",
            "gracePeriodDays",
            "published", "publishedAt",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final String[] SEARCHABLE_FIELDS = {"code", "name", "description"};

    private final PlanRepository repository;
    private final PlanMapper mapper;

    // ─── Read ───────────────────────────────────────────────────────────────

    public PlanDto get(UUID uuid) {
        return mapper.toDto(findManaged(uuid));
    }

    public Page<PlanDto> list(Pageable pageable, String filter, String q) {
        Specification<Plan> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "plan.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(mapper::toDto);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    public PlanDto create(PlanCreateRequest req) {
        // Pre-check duplicate code so the client gets 422 plan.code.duplicate
        // before the V13 UNIQUE index fires a misleading 409.
        if (repository.existsByCode(req.code())) {
            throw new IllegalArgumentException("plan.code.duplicate");
        }

        Plan plan = new Plan();
        plan.setCode(req.code());
        plan.setName(req.name());
        plan.setDescription(req.description());
        plan.setType(req.type());
        plan.setInscriptionFee(req.inscriptionFee());
        plan.setMonthlyFee(req.monthlyFee());
        if (req.includedBeneficiaries() != null) plan.setIncludedBeneficiaries(req.includedBeneficiaries());
        plan.setMaxBeneficiaries(req.maxBeneficiaries());
        plan.setExtraBeneficiaryInscriptionFee(req.extraBeneficiaryInscriptionFee());
        if (req.gracePeriodDays() != null) plan.setGracePeriodDays(req.gracePeriodDays());
        if (req.published() != null) plan.setPublished(req.published());
        plan.setPublishedAt(req.publishedAt());

        // CHECK constraint mirror: max ≥ included keeps the relationship
        // coherent. Pre-checking here gives a clean 422 instead of a 409 from
        // the DB constraint.
        validateBeneficiaryCap(plan);

        return mapper.toDto(repository.save(plan));
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Transactional
    public PlanDto update(UUID uuid, PlanUpdateRequest req) {
        Plan plan = findManaged(uuid);

        if (req.code() != null) {
            repository.findByCode(req.code())
                    .filter(other -> !other.getId().equals(plan.getId()))
                    .ifPresent(other -> { throw new IllegalArgumentException("plan.code.duplicate"); });
            plan.setCode(req.code());
        }

        if (req.name()                              != null) plan.setName(req.name());
        if (req.description()                       != null) plan.setDescription(req.description());
        if (req.type()                              != null) plan.setType(req.type());
        if (req.inscriptionFee()                    != null) plan.setInscriptionFee(req.inscriptionFee());
        if (req.monthlyFee()                        != null) plan.setMonthlyFee(req.monthlyFee());
        if (req.includedBeneficiaries()             != null) plan.setIncludedBeneficiaries(req.includedBeneficiaries());
        if (req.maxBeneficiaries()                  != null) plan.setMaxBeneficiaries(req.maxBeneficiaries());
        if (req.extraBeneficiaryInscriptionFee()    != null) plan.setExtraBeneficiaryInscriptionFee(req.extraBeneficiaryInscriptionFee());
        if (req.gracePeriodDays()                   != null) plan.setGracePeriodDays(req.gracePeriodDays());
        if (req.published()                         != null) plan.setPublished(req.published());
        if (req.publishedAt()                       != null) plan.setPublishedAt(req.publishedAt());
        if (req.active()                            != null) plan.setActive(req.active());
        if (req.status()                            != null) plan.setStatus(req.status());

        validateBeneficiaryCap(plan);

        return mapper.toDto(plan);  // managed → dirty-check on commit
    }

    // ─── Delete (soft) ──────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID uuid) {
        Plan plan = findManaged(uuid);
        plan.setActive(false);
        // Auto-unpublish on soft-delete — a deactivated plan should not keep
        // showing on the public directory just because is_published is true.
        plan.setPublished(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Plan findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("plan.not_found"));
    }

    /**
     * Mirrors the V13 CHECK constraint {@code max_beneficiaries IS NULL OR
     * max_beneficiaries >= included_beneficiaries}. Pre-checking at the
     * service gives a clean 422 instead of a misleading 409 from the DB.
     */
    private static void validateBeneficiaryCap(Plan plan) {
        if (plan.getMaxBeneficiaries() != null
                && plan.getMaxBeneficiaries() < plan.getIncludedBeneficiaries()) {
            throw new IllegalArgumentException("plan.beneficiaries.max_below_included");
        }
    }

    private static Specification<Plan> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
