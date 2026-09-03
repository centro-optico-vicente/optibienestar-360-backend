package com.fenixcore.optibienestar360.modules.corporate.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateBulkEnrollResponse;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateBulkEnrollResponse.Entry;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateBulkEnrollResponse.Outcome;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractCreateRequest;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractDto;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractUpdateRequest;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateMemberBulkRequest;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import com.fenixcore.optibienestar360.modules.corporate.repository.CorporateContractRepository;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberListItemDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for {@link CorporateContract} (v2 PDF — "Contratos
 * Corporativos", V38): admin CRUD + RSQL list, bulk member enrollment, and the
 * per-contract member listing.
 *
 * <p>Plural class name mirrors {@code PlansService} / {@code MembersService}.
 * The person + member building is delegated to
 * {@link MembersService#enrollForCorporate} so the corporate flow reuses the
 * exact same enrollment logic (person dedup, promoter attribution) as the
 * single-member admin endpoint.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorporateContractsService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "institutionName", "institutionTaxId", "payerMode",
            "expectedMemberCount", "actualMemberCount",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final String[] SEARCHABLE_FIELDS = {"institutionName", "institutionTaxId"};

    /** {@code plan_Display} → plan's catalog name (ADR 0014 default; only FK the list DTO surfaces as a display column). */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(CorporateContract.class, Map.of("plan_Display", "plan.name"));

    /** Same aliases as {@code MembersService.SORTABLE_FIELDS} — {@code fullName}/{@code documentType}/{@code documentNumber}/{@code phone} are flattened person columns, not on {@code Member} itself. */
    private static final Map<String, SortFieldValidator.SortableField> MEMBER_SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Member.class, Map.of(
                    "fullName", "person.fullName",
                    "documentType", "person.documentType",
                    "documentNumber", "person.documentNumber",
                    "phone", "person.phone",
                    "city_Display", "person.city.name",
                    "currentPromoter_Display", "promoter.displayName"
            ));

    private final CorporateContractRepository repository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final MembersService membersService;
    private final MemberMapper memberMapper;
    private final DefaultSortResolver defaultSortResolver;

    // ─── Read ───────────────────────────────────────────────────────────────

    public CorporateContractDto get(UUID uuid) {
        return CorporateContractDto.from(findManaged(uuid));
    }

    public Page<CorporateContractDto> list(Pageable pageable, String filter, String q) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "corporate_contract", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "corporate_contract");
        Specification<CorporateContract> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "corporate_contract.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(CorporateContractDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("corporate_contract", pageable);
    }

    /** A contract's active member portfolio ({@code GET /{uuid}/members}). */
    public Page<MemberListItemDto> listMembers(UUID uuid, Pageable pageable) {
        CorporateContract contract = findManaged(uuid);
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "corporate_contract_member", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, MEMBER_SORTABLE_FIELDS, "corporate_contract_member");
        return memberRepository
                .findByCorporateContractIdAndActiveTrue(contract.getId(), resolvedPageable)
                .map(memberMapper::toListItem);
    }

    /** The sort {@link #listMembers} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSortMembers(Pageable pageable) {
        return defaultSortResolver.effectiveSort("corporate_contract_member", pageable);
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "corporate_contract", action = AuditAction.CREATE)
    public CorporateContractDto create(CorporateContractCreateRequest req) {
        Plan plan = resolvePlan(req.planUuid());
        requireCorporatePlan(plan);

        CorporateContract contract = new CorporateContract();
        contract.setPlan(plan);
        contract.setInstitutionName(req.institutionName());
        contract.setInstitutionTaxId(req.institutionTaxId());
        contract.setContactUser(resolveContactUser(req.contactUserUuid()));
        contract.setPayerMode(req.payerMode());
        contract.setExpectedMemberCount(req.expectedMemberCount());
        // actualMemberCount defaults to 0 until members are enrolled.

        return CorporateContractDto.from(repository.save(contract));
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "corporate_contract", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public CorporateContractDto update(UUID uuid, CorporateContractUpdateRequest req) {
        CorporateContract contract = findManaged(uuid);

        if (req.planUuid() != null) {
            Plan plan = resolvePlan(req.planUuid());
            requireCorporatePlan(plan);
            contract.setPlan(plan);
        }
        if (req.institutionName()    != null) contract.setInstitutionName(req.institutionName());
        if (req.institutionTaxId()   != null) contract.setInstitutionTaxId(req.institutionTaxId());
        if (req.contactUserUuid()    != null) contract.setContactUser(resolveContactUser(req.contactUserUuid()));
        if (req.payerMode()          != null) contract.setPayerMode(req.payerMode());
        if (req.expectedMemberCount() != null) contract.setExpectedMemberCount(req.expectedMemberCount());
        if (req.active()             != null) contract.setActive(req.active());
        if (req.status()             != null) contract.setStatus(req.status());

        return CorporateContractDto.from(contract);   // managed → dirty-check on commit
    }

    // ─── Delete (soft) ──────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "corporate_contract", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID uuid) {
        findManaged(uuid).setActive(false);
    }

    // ─── Bulk enrollment ─────────────────────────────────────────────────────

    /**
     * Enrolls a batch of members under the contract in a single transaction.
     * Already-enrolled people are skipped (never fail the batch); any structural
     * error inside {@link MembersService#enrollForCorporate} (unknown catalog
     * UUID, etc.) propagates and rolls the whole batch back. After enrolling,
     * {@code actual_member_count} is re-synced from the live portfolio so it
     * stays accurate regardless of prior skips or removals.
     */
    @Transactional
    public CorporateBulkEnrollResponse enrollMembers(UUID uuid, CorporateMemberBulkRequest req) {
        CorporateContract contract = findManaged(uuid);

        List<Entry> results = new ArrayList<>(req.members().size());
        int enrolled = 0;
        int skipped = 0;
        for (MemberCreateRequest memberReq : req.members()) {
            Member member = membersService.enrollForCorporate(memberReq, contract);
            if (member == null) {
                skipped++;
                results.add(new Entry(memberReq.documentType(), memberReq.documentNumber(),
                        fullName(memberReq), Outcome.SKIPPED_DUPLICATE, null));
            } else {
                enrolled++;
                results.add(new Entry(memberReq.documentType(), memberReq.documentNumber(),
                        fullName(memberReq), Outcome.ENROLLED, member.getUuid()));
            }
        }

        // Re-sync from the live portfolio (auto-flush makes the just-saved rows
        // visible to the count) rather than blindly incrementing.
        contract.setActualMemberCount(
                (int) memberRepository.countByCorporateContractIdAndActiveTrue(contract.getId()));

        return new CorporateBulkEnrollResponse(req.members().size(), enrolled, skipped, results);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CorporateContract findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("corporate_contract.not_found"));
    }

    private Plan resolvePlan(UUID uuid) {
        return planRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("plan.not_found"));
    }

    private User resolveContactUser(UUID uuid) {
        if (uuid == null) return null;
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
    }

    /**
     * A corporate contract must run against a {@code CORPORATIVO} plan — the DB
     * CHECK can't reach across to {@code plans.type}, so it is enforced here.
     */
    private static void requireCorporatePlan(Plan plan) {
        if (plan.getType() != Plan.PlanType.CORPORATIVO) {
            throw new IllegalArgumentException("corporate_contract.plan.not_corporate");
        }
    }

    private static String fullName(MemberCreateRequest req) {
        return (req.firstName() + " " + req.lastName()).trim();
    }

    private static Specification<CorporateContract> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
