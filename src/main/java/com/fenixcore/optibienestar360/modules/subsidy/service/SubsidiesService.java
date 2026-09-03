package com.fenixcore.optibienestar360.modules.subsidy.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyAuditLogDto;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyBeneficiaryRequest;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyCreateRequest;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyDto;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyUpdateRequest;
import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyAuditLog;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyBeneficiary;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyAuditLogRepository;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for {@link Subsidy} (v2 PDF item #1, V41) — admin CRUD
 * with a mandatory audit trail ({@link SubsidyAuditLog}) plus the self-service
 * read for the titular.
 *
 * <p>Plural class name mirrors {@code PlansService} / {@code CorporateContractsService}.
 * Every mutating operation (create / modify / revoke) writes an immutable audit
 * entry with a before/after snapshot and the acting user — the "registro formal
 * en auditoría" the PDF requires.</p>
 *
 * <p>Validation enforced here (the DB CHECKs can't reach across rows):
 * at least one covered fee ({@code subsidy.coverage.required}), a coherent
 * validity window ({@code subsidy.valid_until.before_from}), listed beneficiaries
 * belonging to the member ({@code subsidy.beneficiary.not_in_member}), and the
 * beneficiary count within {@code max_exonerated_beneficiaries} — or, when unset,
 * the plan's own {@code max_beneficiaries} ({@code subsidy.beneficiaries.cap_exceeded}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubsidiesService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "monthlyPercentage", "inscriptionPercentage", "maxExoneratedBeneficiaries",
            "validFrom", "validUntil", "active", "status", "createdAt", "updatedAt"
    );

    private static final String[] SEARCHABLE_FIELDS = {"reason"};

    /** {@code member_Display} is a 2-hop path — {@code Subsidy.member} then {@code Member.person.fullName} (see {@code SubsidyDto.from}). */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Subsidy.class, Map.of("member_Display", "member.person.fullName"));

    private final SubsidyRepository repository;
    private final SubsidyAuditLogRepository auditLogRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final DefaultSortResolver defaultSortResolver;

    // ─── Read ───────────────────────────────────────────────────────────────

    public SubsidyDto get(UUID uuid) {
        return SubsidyDto.from(findManaged(uuid));
    }

    public Page<SubsidyDto> list(Pageable pageable, UUID memberUuid, String filter, String q) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "subsidy", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "subsidy");
        Specification<Subsidy> spec = activeOnly();
        if (memberUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("member").get("uuid"), memberUuid));
        }
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "subsidy.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(SubsidyDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("subsidy", pageable);
    }

    /** Self-service {@code GET /v1/me/subsidies} — the caller's own live subsidies. */
    public List<SubsidyDto> listForUser(UUID userUuid) {
        Member member = memberRepository.findByUserUuid(userUuid)
                .filter(Member::isActive)
                .orElseThrow(() -> new NoSuchElementException("me.member.not_enrolled"));
        return repository.findByMemberIdAndActiveTrueOrderByValidFromDesc(member.getId()).stream()
                .map(SubsidyDto::from)
                .toList();
    }

    public List<SubsidyAuditLogDto> getLog(UUID uuid) {
        Subsidy subsidy = findManaged(uuid);
        return auditLogRepository.findBySubsidyIdOrderByCreatedAtDesc(subsidy.getId()).stream()
                .map(SubsidyAuditLogDto::from)
                .toList();
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "subsidy", action = AuditAction.CREATE)
    public SubsidyDto create(SubsidyCreateRequest req, UUID actorUuid) {
        Member member = resolveMember(req.memberUuid());
        User actor = resolveUser(actorUuid);

        Subsidy subsidy = new Subsidy();
        subsidy.setMember(member);
        subsidy.setMonthlyPercentage(req.monthlyPercentage());
        subsidy.setInscriptionPercentage(req.inscriptionPercentage());
        subsidy.setMaxExoneratedBeneficiaries(req.maxExoneratedBeneficiaries());
        subsidy.setReason(req.reason());
        subsidy.setAuthorizedBy(actor);
        subsidy.setValidFrom(req.validFrom());
        subsidy.setValidUntil(req.validUntil());

        requireCoverage(subsidy);
        requireCoherentWindow(subsidy);
        applyBeneficiaryLines(subsidy, member, req.beneficiaries());

        Subsidy saved = repository.save(subsidy);
        writeAudit(saved, SubsidyAuditLog.Action.CREATED, actor, null, snapshot(saved), req.reason());
        return SubsidyDto.from(saved);
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "subsidy", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public SubsidyDto update(UUID uuid, SubsidyUpdateRequest req, UUID actorUuid) {
        Subsidy subsidy = findManaged(uuid);
        User actor = resolveUser(actorUuid);
        Map<String, Object> before = snapshot(subsidy);

        if (req.monthlyPercentage() != null)          subsidy.setMonthlyPercentage(req.monthlyPercentage());
        if (req.inscriptionPercentage() != null)      subsidy.setInscriptionPercentage(req.inscriptionPercentage());
        if (req.maxExoneratedBeneficiaries() != null) subsidy.setMaxExoneratedBeneficiaries(req.maxExoneratedBeneficiaries());
        if (req.reason() != null)                     subsidy.setReason(req.reason());
        if (req.validFrom() != null)                  subsidy.setValidFrom(req.validFrom());
        if (req.validUntil() != null)                 subsidy.setValidUntil(req.validUntil());
        if (req.active() != null)                     subsidy.setActive(req.active());

        // A non-null list replaces the whole set (orphanRemoval clears the old rows).
        if (req.beneficiaries() != null) {
            subsidy.getBeneficiaries().clear();
            applyBeneficiaryLines(subsidy, subsidy.getMember(), req.beneficiaries());
        }

        requireCoverage(subsidy);
        requireCoherentWindow(subsidy);
        requireWithinCap(subsidy, subsidy.getBeneficiaries().size());

        writeAudit(subsidy, SubsidyAuditLog.Action.MODIFIED, actor, before, snapshot(subsidy), req.reason());
        return SubsidyDto.from(subsidy);   // managed → dirty-check on commit
    }

    // ─── Revoke (soft delete) ─────────────────────────────────────────────────

    @Transactional
    @Auditable(entity = "subsidy", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void revoke(UUID uuid, UUID actorUuid) {
        Subsidy subsidy = findManaged(uuid);
        User actor = resolveUser(actorUuid);
        Map<String, Object> before = snapshot(subsidy);

        subsidy.setActive(false);

        writeAudit(subsidy, SubsidyAuditLog.Action.REVOKED, actor, before, snapshot(subsidy), null);
    }

    // ─── Beneficiary lines ────────────────────────────────────────────────────

    /**
     * Builds and attaches the per-beneficiary exoneration rows, validating that
     * each beneficiary belongs to the member, that each row covers something, and
     * that the total stays within the effective cap. A beneficiary fully
     * exonerated for inscription has its {@code extra_inscription_paid} flag set —
     * voiding any outstanding PENDING inscription payment stays a manual admin
     * step (documented follow-up).
     */
    private void applyBeneficiaryLines(Subsidy subsidy, Member member, List<SubsidyBeneficiaryRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            requireWithinCap(subsidy, 0);
            return;
        }
        requireWithinCap(subsidy, lines.size());

        for (SubsidyBeneficiaryRequest line : lines) {
            Beneficiary beneficiary = beneficiaryRepository.findByUuid(line.beneficiaryUuid())
                    .orElseThrow(() -> new NoSuchElementException("beneficiary.not_found"));
            if (beneficiary.getMember() == null || !beneficiary.getMember().getId().equals(member.getId())) {
                throw new IllegalArgumentException("subsidy.beneficiary.not_in_member");
            }
            if (line.monthlyPercentage() == null && line.inscriptionPercentage() == null) {
                throw new IllegalArgumentException("subsidy.coverage.required");
            }

            SubsidyBeneficiary row = new SubsidyBeneficiary();
            row.setBeneficiary(beneficiary);
            row.setMonthlyPercentage(line.monthlyPercentage());
            row.setInscriptionPercentage(line.inscriptionPercentage());
            subsidy.addBeneficiary(row);

            // Full inscription exoneration settles the beneficiary's extra fee flag.
            if (line.inscriptionPercentage() != null && line.inscriptionPercentage().compareTo(HUNDRED) >= 0) {
                beneficiary.setExtraInscriptionPaid(true);
            }
        }
    }

    /**
     * Effective cap: the subsidy's own {@code max_exonerated_beneficiaries} when
     * set, otherwise the member's active-membership plan {@code max_beneficiaries}.
     * {@code null} either way = no explicit cap.
     */
    private void requireWithinCap(Subsidy subsidy, int rowCount) {
        Integer cap = subsidy.getMaxExoneratedBeneficiaries();
        if (cap == null) {
            cap = membershipRepository.findFirstByMemberIdAndActiveTrue(subsidy.getMember().getId())
                    .map(m -> m.getPlan().getMaxBeneficiaries())
                    .orElse(null);
        }
        if (cap != null && rowCount > cap) {
            throw new IllegalArgumentException("subsidy.beneficiaries.cap_exceeded");
        }
    }

    private static void requireCoverage(Subsidy s) {
        if (s.getMonthlyPercentage() == null && s.getInscriptionPercentage() == null) {
            throw new IllegalArgumentException("subsidy.coverage.required");
        }
    }

    private static void requireCoherentWindow(Subsidy s) {
        if (s.getValidUntil() != null && s.getValidUntil().isBefore(s.getValidFrom())) {
            throw new IllegalArgumentException("subsidy.valid_until.before_from");
        }
    }

    // ─── Audit ────────────────────────────────────────────────────────────────

    private void writeAudit(Subsidy subsidy, SubsidyAuditLog.Action action, User actor,
                            Map<String, Object> before, Map<String, Object> after, String reason) {
        SubsidyAuditLog log = new SubsidyAuditLog();
        log.setSubsidy(subsidy);
        log.setAction(action);
        log.setActor(actor);
        log.setBefore(before);
        log.setAfter(after);
        log.setReason(reason);
        auditLogRepository.save(log);
    }

    private static Map<String, Object> snapshot(Subsidy s) {
        Map<String, Object> m = new HashMap<>();
        m.put("monthlyPercentage", s.getMonthlyPercentage());
        m.put("inscriptionPercentage", s.getInscriptionPercentage());
        m.put("maxExoneratedBeneficiaries", s.getMaxExoneratedBeneficiaries());
        m.put("reason", s.getReason());
        m.put("validFrom", s.getValidFrom() != null ? s.getValidFrom().toString() : null);
        m.put("validUntil", s.getValidUntil() != null ? s.getValidUntil().toString() : null);
        m.put("active", s.isActive());
        m.put("beneficiaryCount", s.getBeneficiaries() != null ? s.getBeneficiaries().size() : 0);
        return m;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Subsidy findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("subsidy.not_found"));
    }

    private Member resolveMember(UUID uuid) {
        return memberRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
    }

    private User resolveUser(UUID uuid) {
        return userRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
    }

    private static Specification<Subsidy> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
