package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.OptionsSupport;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.corporate.repository.CorporateContractRepository;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanUpdateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.PublicPlanDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.mapper.PlanMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
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
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Plan.class, Map.of());

    private final PlanRepository repository;
    private final PlanMapper mapper;
    private final MembershipRepository membershipRepository;
    private final CorporateContractRepository corporateContractRepository;
    private final DefaultSortResolver defaultSortResolver;

    // ─── Read ───────────────────────────────────────────────────────────────

    public PlanDto get(UUID uuid) {
        return mapper.toDto(findManaged(uuid));
    }

    public Page<PlanDto> list(Pageable pageable, String filter, String q, boolean includeInactive) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "plan", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "plan");
        Specification<Plan> spec = includeInactive ? (root, query, cb) -> cb.conjunction() : activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "plan.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(mapper::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("plan", pageable);
    }

    /** Lightweight options for select/dropdown population — see {@link OptionsSupport}. */
    public List<OptionDto> listOptions(String q, int limit, List<UUID> currentValues) {
        Specification<Plan> spec = activeOnly().and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        return OptionsSupport.build(repository, repository::findByUuid, spec, currentValues, limit,
                Plan::getUuid, Plan::getCode, PlansService::labelOf, Plan::isActive);
    }

    private static String labelOf(Plan plan) {
        return plan.getCode() + " — " + plan.getName();
    }

    // ─── Public read (anonymous pricing surface) ─────────────────────────────

    /**
     * Published + active plans for the anonymous pricing page
     * ({@code GET /v1/public/plans}). Returns the sanitized {@link PublicPlanDto}
     * — audit / status / publishing internals stay out. Backed by the V13
     * partial index {@code (is_active) WHERE is_active AND is_published},
     * created specifically for this public directory.
     */
    public Page<PublicPlanDto> publicList(Pageable pageable) {
        return repository.findAll(publiclyVisible(), pageable).map(mapper::toPublicDto);
    }

    /**
     * A single plan for an anonymous detail / deep-link view. 404s when the
     * plan doesn't exist OR is not publicly visible (inactive / unpublished) —
     * anonymous callers never learn that an unpublished plan exists, matching
     * the {@code PublicAllyController} contract.
     */
    public PublicPlanDto publicGetByUuid(UUID uuid) {
        Plan plan = repository.findByUuid(uuid)
                .filter(Plan::isActive)
                .filter(Plan::isPublished)
                .orElseThrow(() -> new NoSuchElementException("plan.not_found"));
        return mapper.toPublicDto(plan);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "plan", action = AuditAction.CREATE)
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
    @Auditable(entity = "plan", action = AuditAction.UPDATE, uuidArgIndex = 0)
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

    /**
     * Counts real FK references to this plan: {@code memberships.plan_id}
     * and {@code corporate_contracts.plan_id}, both {@code @ManyToOne Plan}
     * with no cascade declared on the Plan side (Plan has no reverse
     * {@code @OneToMany} at all), so both are genuine hard-delete blockers.
     */
    public long countUsages(UUID uuid) {
        Plan plan = findManaged(uuid);
        long memberships = membershipRepository.countByPlanId(plan.getId());
        long corporateContracts = corporateContractRepository.countByPlanId(plan.getId());
        return memberships + corporateContracts;
    }

    public UsageDto getUsage(UUID uuid) {
        long count = countUsages(uuid);
        return new UsageDto(count > 0, count);
    }

    /**
     * Smart delete: hard-deletes only when {@code physical=true} AND the
     * plan is genuinely unreferenced (re-checked here, not trusted from the
     * caller, to avoid a race between the usage check and the delete).
     * Otherwise falls back to the existing soft-delete + auto-unpublish.
     * Omitting {@code physical} (default {@code false}) reproduces the prior
     * behavior exactly.
     */
    @Transactional
    @Auditable(entity = "plan", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid, boolean physical) {
        Plan plan = findManaged(uuid);
        long usages = countUsages(uuid);
        if (physical && usages == 0) {
            repository.delete(plan);
            return;
        }
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

    /** Plans an anonymous caller may see: active AND published. */
    private static Specification<Plan> publiclyVisible() {
        return (root, query, cb) -> cb.and(cb.isTrue(root.get("active")), cb.isTrue(root.get("published")));
    }
}
