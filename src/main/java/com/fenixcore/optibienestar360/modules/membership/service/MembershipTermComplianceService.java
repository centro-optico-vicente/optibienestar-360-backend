package com.fenixcore.optibienestar360.modules.membership.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.AuditContextResolver;
import com.fenixcore.optibienestar360.core.audit.DataChangeAuditWriter;
import com.fenixcore.optibienestar360.core.audit.EntityConfigService;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanTermApplyRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanTermApplyResultDto;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipTermRowDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanTermSummaryDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Compliance of the existing memberships of a plan with the plan's current term rule, and bulk application of that
 * rule (hub ADR 0034). Each membership keeps its own snapshot, so after the plan's rule changes the admin needs to
 * see which memberships comply and apply the rule to those that do not.
 *
 * <p>A membership <b>complies</b> when its snapshot equals the plan's rule: same {@code reinscription_required},
 * {@code term_mode} and fixed month/day or period months. The expiry date is not part of the rule, so an
 * overridden expiry with the same rule still complies. Only live memberships count ({@code is_active} and
 * ACTIVE / SUSPENDED / EXPIRED).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MembershipTermComplianceService {

    static final String AUDIT_ENTITY = "membership";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Caracas");
    private static final List<String> LIVE_STATUSES = List.of("ACTIVE", "SUSPENDED", "EXPIRED");
    private static final Set<String> SORTABLE = Set.of("enrolledAt", "expiresAt", "status");

    /** Values of the {@code compliance} filter. */
    public enum Compliance { COMPLIES, DIFFERS }

    private final PlanRepository planRepository;
    private final MembershipRepository membershipRepository;
    private final MembershipMapper membershipMapper;
    private final EntityConfigService entityConfigService;
    private final AuditContextResolver auditContextResolver;
    private final DataChangeAuditWriter dataChangeAuditWriter;
    private final ObjectMapper objectMapper;

    // ─── Rule ───────────────────────────────────────────────────────────────

    /** Whether the membership snapshot equals the plan's current rule (the expiry date is not compared). */
    static boolean complies(Membership membership, Plan plan) {
        return Objects.equals(MembershipTermRule.of(membership), MembershipTermRule.of(plan));
    }

    /** Whether the membership is live: active and not CANCELED. */
    static boolean isLive(Membership membership) {
        return membership.isActive() && LIVE_STATUSES.contains(membership.getStatus());
    }

    // ─── Read ───────────────────────────────────────────────────────────────

    public PlanTermSummaryDto summary(UUID planUuid) {
        Plan plan = findPlan(planUuid);
        long total = membershipRepository.count(liveOfPlan(plan));
        long complies = membershipRepository.count(liveOfPlan(plan).and(compliesSpec(plan)));
        MembershipTermRule rule = MembershipTermRule.of(plan);
        LocalDate suggested = rule == null ? null : rule.firstExpiry(today());
        return new PlanTermSummaryDto(total, complies, total - complies, rule != null,
                plan.getTermMode(), plan.getTermFixedMonth(), plan.getTermFixedDay(), plan.getTermPeriodMonths(),
                suggested);
    }

    public Page<MembershipTermRowDto> list(UUID planUuid, Compliance compliance, String q, Pageable pageable) {
        Plan plan = findPlan(planUuid);
        Specification<Membership> spec = liveOfPlan(plan);
        if (compliance == Compliance.COMPLIES) {
            spec = spec.and(compliesSpec(plan));
        } else if (compliance == Compliance.DIFFERS) {
            spec = spec.and(Specification.not(compliesSpec(plan)));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q,
                    "member.person.firstName", "member.person.lastName", "member.person.documentNumber"));
        }
        Sort sort = Sort.by(pageable.getSort().stream().filter(o -> SORTABLE.contains(o.getProperty())).toList());
        if (sort.isEmpty()) {
            sort = Sort.by("enrolledAt").descending();
        }
        // Stable tie-break so pages do not shuffle when the sort key repeats.
        sort = sort.and(Sort.by("id"));
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        return membershipRepository.findAll(spec, page).map(m -> toRow(m, plan));
    }

    private MembershipTermRowDto toRow(Membership m, Plan plan) {
        Member member = m.getMember();
        return new MembershipTermRowDto(m.getUuid(), member.getUuid(), DisplayRefs.ref(member),
                member.getPerson() == null ? null : member.getPerson().getDocumentNumber(),
                m.getContractNumber(), m.getStatus(), m.getEnrolledAt(), m.getExpiresAt(),
                m.isReinscriptionRequired(), m.getTermMode(), m.getTermFixedMonth(), m.getTermFixedDay(),
                m.getTermPeriodMonths(), m.getTermStartedAt(), complies(m, plan));
    }

    // ─── Apply ──────────────────────────────────────────────────────────────

    /**
     * Copies the plan's rule onto the target memberships and sets the term to start today and end on
     * {@code req.expiresAt()}. {@code inscription_due_since} is left untouched. CANCELED / inactive / foreign-plan
     * memberships are skipped and counted. One transaction; one audit entry per updated membership (explicit
     * recording, same precedent as the commission recorder: the AOP aspect handles one uuid per call).
     */
    @Transactional
    public PlanTermApplyResultDto apply(UUID planUuid, PlanTermApplyRequest req) {
        Plan plan = findPlan(planUuid);
        MembershipTermRule rule = MembershipTermRule.of(plan);
        if (rule == null) {
            throw new IllegalArgumentException("membership.term.apply_plan_not_reinscription");
        }
        LocalDate today = today();
        if (req.expiresAt() == null || !req.expiresAt().isAfter(today)) {
            throw new IllegalArgumentException("membership.term.apply_expires_not_future");
        }

        List<Membership> targets;
        int skipped = 0;
        List<UUID> requested = req.membershipUuids() == null ? List.of() : req.membershipUuids().stream().distinct().toList();
        if (requested.isEmpty()) {
            targets = membershipRepository.findAll(liveOfPlan(plan).and(Specification.not(compliesSpec(plan))));
        } else {
            Specification<Membership> byUuids = (root, query, cb) -> root.get("uuid").in(requested);
            List<Membership> found = membershipRepository.findAll(byUuids);
            targets = new ArrayList<>();
            Set<UUID> foundUuids = new HashSet<>();
            for (Membership m : found) {
                foundUuids.add(m.getUuid());
                boolean ownPlan = m.getPlan().getId().equals(plan.getId());
                if (ownPlan && isLive(m)) {
                    targets.add(m);
                } else {
                    skipped++;
                }
            }
            skipped += (int) requested.stream().filter(u -> !foundUuids.contains(u)).count();
        }

        for (Membership m : targets) {
            Map<String, Object> before = snapshot(m);
            m.setReinscriptionRequired(true);
            m.setTermMode(rule.mode());
            m.setTermFixedMonth(rule.fixedMonth());
            m.setTermFixedDay(rule.fixedDay());
            m.setTermPeriodMonths(rule.periodMonths());
            m.setTermStartedAt(today);
            m.setExpiresAt(req.expiresAt());
            recordUpdate(m.getUuid(), before, snapshot(m));
        }
        membershipRepository.saveAll(targets);
        log.info("Plan {} term applied: applied={} skipped={} expiresAt={}", planUuid, targets.size(), skipped, req.expiresAt());
        return new PlanTermApplyResultDto(targets.size(), skipped);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Plan findPlan(UUID uuid) {
        return planRepository.findByUuid(uuid).orElseThrow(() -> new NoSuchElementException("plan.not_found"));
    }

    private static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private static Specification<Membership> liveOfPlan(Plan plan) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("plan").get("id"), plan.getId()),
                cb.isTrue(root.get("active")),
                root.get("status").in(LIVE_STATUSES));
    }

    /** SQL mirror of {@link #complies}: every rule column equals the plan's (null-safe). */
    static Specification<Membership> compliesSpec(Plan plan) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("reinscriptionRequired"), plan.isReinscriptionRequired()));
            boolean ruleSet = plan.isReinscriptionRequired() && plan.getTermMode() != null;
            all.add(nullSafeEqual(cb, root.get("termMode"), ruleSet ? plan.getTermMode() : null));
            all.add(nullSafeEqual(cb, root.get("termFixedMonth"), ruleSet ? plan.getTermFixedMonth() : null));
            all.add(nullSafeEqual(cb, root.get("termFixedDay"), ruleSet ? plan.getTermFixedDay() : null));
            all.add(nullSafeEqual(cb, root.get("termPeriodMonths"), ruleSet ? plan.getTermPeriodMonths() : null));
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    private static Predicate nullSafeEqual(jakarta.persistence.criteria.CriteriaBuilder cb, Path<?> path, Object value) {
        return value == null ? cb.isNull(path) : cb.equal(path, value);
    }

    // ─── Audit (explicit, never blocks the business operation) ──────────────

    private Map<String, Object> snapshot(Membership membership) {
        try {
            return toJsonMap(membershipMapper.toDto(membership));
        } catch (Exception e) {
            log.debug("Could not snapshot membership {} for audit", membership.getUuid(), e);
            return null;
        }
    }

    private void recordUpdate(UUID uuid, Map<String, Object> before, Map<String, Object> after) {
        try {
            if (!entityConfigService.isEnabled(AUDIT_ENTITY, AuditAction.UPDATE)) {
                return;
            }
            boolean capture = entityConfigService.captureBeforeAfter(AUDIT_ENTITY);
            dataChangeAuditWriter.write(AUDIT_ENTITY, uuid, AuditAction.UPDATE,
                    capture ? before : null, capture ? after : null,
                    auditContextResolver.resolveActorId().orElse(null), null);
        } catch (Exception e) {
            log.warn("Failed to persist data_change_audit_log for membership {} (UPDATE) - business operation continues", uuid, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toJsonMap(Object value) {
        return value == null ? null : objectMapper.convertValue(value, Map.class);
    }
}
