package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateFilter;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateSource;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionMode;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionResultDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Portfolio distribution (ADR LOC-0016): lists the members that can be moved — the unassigned ones
 * (no advisor or the INSTITUCION system advisor) or one advisor's portfolio, active or not — and
 * deals a selection of them to one or more advisors, in equal parts (round-robin) or by count.
 *
 * <p>The write goes through {@link MemberPromoterService#reassignAll}, so every moved member gets
 * the same {@code member_promoter_assignments} audit row (with the reason) and notification as a
 * single reassignment. The whole distribution is one transaction: any failure rolls everything
 * back. The preview runs the very same computation without writing.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberPortfolioDistributionService {

    /** Upper bound of members one distribution may touch (422 beyond). */
    public static final int MAX_MEMBERS = 2000;

    /** Upper bound of target advisors in one distribution (422 beyond). */
    public static final int MAX_TARGETS = 20;

    /** {@code ?sort=} keys the candidates table may send, mapped to their JPA paths. */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Member.class, Map.of(
                    "displayName", "person.fullName",
                    "document", "person.documentNumber",
                    "promoter_Display", "promoter.displayName"
            ));

    /** Stable order of the dealing: name (case-insensitive), then uuid. */
    private static final Comparator<Member> STABLE_ORDER = Comparator
            .comparing((Member m) -> nameOf(m).toLowerCase(Locale.ROOT))
            .thenComparing(Member::getUuid);

    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final PromoterRepository promoterRepository;
    private final MemberPromoterService memberPromoterService;

    // ─── Candidates ─────────────────────────────────────────────────────────

    /**
     * Page of active members matching {@code filter}; sortable by {@code displayName} (default, asc),
     * {@code createdAt}, {@code document} and {@code promoter_Display}, always tie-broken by uuid. Each
     * row carries the member status, the plan of the active membership and the current advisor.
     */
    public Page<MemberCandidateDto> candidates(MemberCandidateFilter filter, Pageable pageable) {
        Specification<Member> spec = candidatesSpec(filter);
        Page<Member> page = memberRepository.findAll(spec, withStableSort(pageable));
        Map<Long, Membership> memberships = activeMemberships(page.getContent());
        return page.map(m -> toCandidate(m, memberships.get(m.getId())));
    }

    // ─── Distribution ───────────────────────────────────────────────────────

    /** Computes the split of {@link #distribute} without writing anything. */
    public MemberDistributionResultDto preview(MemberDistributionRequest request) {
        return split(request).result();
    }

    /**
     * Deals the selection to the targets and reassigns every member not already on the advisor it
     * was dealt to — all or nothing, in this transaction.
     */
    @Transactional
    public MemberDistributionResultDto distribute(MemberDistributionRequest request, UUID actorUserUuid) {
        Split split = split(request);
        if (!split.moves().isEmpty()) {
            memberPromoterService.reassignAll(split.moves(), request.reason().trim(), actorUserUuid);
        }
        return split.result();
    }

    /** Validates the request, resolves the selection and deals it; shared by preview and distribute. */
    private Split split(MemberDistributionRequest request) {
        List<Promoter> targets = resolveTargets(request);
        List<Member> members = resolveSelection(request);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("member.distribution.empty");
        }

        List<Promoter> dealt = new ArrayList<>(members.size());
        if (request.mode() == MemberDistributionMode.COUNT) {
            int sum = 0;
            for (MemberDistributionRequest.Target t : request.targets()) {
                sum += t.count();
            }
            if (sum > members.size()) {
                throw new IllegalArgumentException("member.distribution.count_exceeds");
            }
            for (int i = 0; i < targets.size(); i++) {
                for (int k = 0; k < request.targets().get(i).count(); k++) {
                    dealt.add(targets.get(i));
                }
            }
        } else {
            for (int i = 0; i < members.size(); i++) {
                dealt.add(targets.get(i % targets.size()));
            }
        }

        Map<Long, Integer> counts = new LinkedHashMap<>();
        targets.forEach(t -> counts.put(t.getId(), 0));
        List<Map.Entry<Member, Promoter>> moves = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < dealt.size(); i++) {
            Member member = members.get(i);
            Promoter target = dealt.get(i);
            Promoter current = member.getPromoter();
            if (current != null && current.getId().equals(target.getId())) {
                skipped++;
                continue;
            }
            moves.add(new AbstractMap.SimpleImmutableEntry<>(member, target));
            counts.merge(target.getId(), 1, Integer::sum);
        }

        List<MemberDistributionResultDto.TargetResult> perTarget = targets.stream()
                .map(t -> new MemberDistributionResultDto.TargetResult(t.getUuid(), t.getDisplayName(),
                        counts.get(t.getId())))
                .toList();
        return new Split(moves, new MemberDistributionResultDto(dealt.size(), skipped, perTarget));
    }

    /** 1..20 distinct, existing, active, non-system advisors; counts ≥ 1 in {@code COUNT} mode. */
    private List<Promoter> resolveTargets(MemberDistributionRequest request) {
        List<MemberDistributionRequest.Target> requested = request.targets();
        if (requested == null || requested.isEmpty() || requested.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("member.distribution.targets_invalid");
        }
        Set<UUID> seen = new HashSet<>();
        List<Promoter> targets = new ArrayList<>(requested.size());
        for (MemberDistributionRequest.Target t : requested) {
            if (!seen.add(t.promoterUuid())) {
                throw new IllegalArgumentException("member.distribution.targets_duplicate");
            }
            if (request.mode() == MemberDistributionMode.COUNT && (t.count() == null || t.count() < 1)) {
                throw new IllegalArgumentException("member.distribution.count_required");
            }
            Promoter promoter = promoterRepository.findByUuid(t.promoterUuid())
                    .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
            if (!promoter.isActive()) {
                throw new IllegalArgumentException("promoter.inactive");
            }
            if (promoter.isSystem()) {
                throw new IllegalArgumentException("promoter.system_not_assignable");
            }
            targets.add(promoter);
        }
        return targets;
    }

    /**
     * The members to deal, in the stable order: the explicit {@code memberUuids} or every candidate
     * of {@code filter}, minus {@code excludeMemberUuids}. Exactly one of the two must be sent.
     */
    private List<Member> resolveSelection(MemberDistributionRequest request) {
        boolean byUuids = request.memberUuids() != null && !request.memberUuids().isEmpty();
        boolean byFilter = request.filter() != null;
        if (byUuids == byFilter) {
            throw new IllegalArgumentException("member.distribution.selection_invalid");
        }
        Set<UUID> excluded = request.excludeMemberUuids() == null
                ? Set.of() : new HashSet<>(request.excludeMemberUuids());

        List<Member> members;
        if (byUuids) {
            Set<UUID> uuids = new LinkedHashSet<>(request.memberUuids());
            uuids.removeAll(excluded);
            if (uuids.size() > MAX_MEMBERS) {
                throw new IllegalArgumentException("member.distribution.too_many");
            }
            members = new ArrayList<>(uuids.size());
            for (UUID uuid : uuids) {
                Member member = memberRepository.findByUuid(uuid)
                        .orElseThrow(() -> new NoSuchElementException("member.not_found"));
                if (!member.isActive()) {
                    throw new IllegalArgumentException("member.distribution.member_inactive");
                }
                members.add(member);
            }
        } else {
            Specification<Member> spec = candidatesSpec(request.filter());
            // Cheap guard before loading: even if every exclusion matches, the rest is over the cap.
            if (memberRepository.count(spec) - excluded.size() > MAX_MEMBERS) {
                throw new IllegalArgumentException("member.distribution.too_many");
            }
            members = memberRepository.findAll(spec).stream()
                    .filter(m -> !excluded.contains(m.getUuid()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (members.size() > MAX_MEMBERS) {
                throw new IllegalArgumentException("member.distribution.too_many");
            }
        }
        members.sort(STABLE_ORDER);
        return members;
    }

    // ─── Specification ──────────────────────────────────────────────────────

    /** Active members of the source (unassigned or one advisor), narrowed by text, plan (of the active membership) and member status. */
    private Specification<Member> candidatesSpec(MemberCandidateFilter f) {
        if (f == null || f.source() == null) {
            throw new IllegalArgumentException("member.candidates.source_required");
        }
        Specification<Member> spec = (root, query, cb) -> cb.isTrue(root.get("active"));
        if (f.source() == MemberCandidateSource.UNASSIGNED) {
            spec = spec.and((root, query, cb) -> MembersService.unassignedPredicate(root, cb));
        } else {
            if (f.sourcePromoterUuid() == null) {
                throw new IllegalArgumentException("member.candidates.source_promoter_required");
            }
            // Inactive and system advisors are valid sources: their portfolio is what gets moved.
            Promoter source = promoterRepository.findByUuid(f.sourcePromoterUuid())
                    .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
            Long sourceId = source.getId();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("promoter").get("id"), sourceId));
        }
        if (f.q() != null && !f.q().isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(f.q(), "person.fullName", "person.documentNumber"));
        }
        if (f.planUuid() != null) {
            UUID planUuid = f.planUuid();
            spec = spec.and(withActiveMembership((ms, cb) -> cb.equal(ms.get("plan").get("uuid"), planUuid)));
        }
        if (f.status() != null && !f.status().isBlank()) {
            String status = f.status().trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return spec;
    }

    /** Members whose active membership matches {@code condition} ({@code IN} subquery, no row duplication). */
    private static Specification<Member> withActiveMembership(
            java.util.function.BiFunction<Root<Membership>, jakarta.persistence.criteria.CriteriaBuilder,
                    jakarta.persistence.criteria.Predicate> condition) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<Membership> ms = sq.from(Membership.class);
            sq.select(ms.get("member").get("id"))
                    .where(cb.isTrue(ms.get("active")), condition.apply(ms, cb));
            return root.get("id").in(sq);
        };
    }

    /** The requested sort (unknown keys dropped) or name ascending, always tie-broken by uuid. */
    private static Pageable withStableSort(Pageable pageable) {
        Pageable resolved = SortFieldValidator.resolve(pageable, SORTABLE_FIELDS, "member_candidate");
        Sort sort = resolved.getSort().isUnsorted()
                ? Sort.by(Sort.Order.asc("person.fullName").ignoreCase())
                : resolved.getSort();
        sort = sort.and(Sort.by(Sort.Order.asc("uuid")));
        return resolved.isUnpaged()
                ? Pageable.unpaged(sort)
                : PageRequest.of(resolved.getPageNumber(), resolved.getPageSize(), sort);
    }

    // ─── Mapping ────────────────────────────────────────────────────────────

    private Map<Long, Membership> activeMemberships(List<Member> members) {
        if (members.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = members.stream().map(Member::getId).toList();
        return membershipRepository.findActiveWithPlanByMemberIds(ids).stream()
                .collect(Collectors.toMap(ms -> ms.getMember().getId(), Function.identity(), (a, b) -> a));
    }

    private static MemberCandidateDto toCandidate(Member m, Membership membership) {
        Person person = m.getPerson();
        Plan plan = membership != null ? membership.getPlan() : null;
        Promoter promoter = m.getPromoter();
        return new MemberCandidateDto(
                m.getUuid(),
                person != null ? person.getFullName() : null,
                formatDocument(person),
                plan != null ? DisplayRef.of(plan.getUuid(), null, plan.getName()) : null,
                m.getStatus(),
                promoter != null ? DisplayRef.of(promoter.getUuid(), null, promoter.getDisplayName()) : null,
                m.getCreatedAt());
    }

    /** {@code V-12345678}; just the number when the type is missing. */
    static String formatDocument(Person person) {
        if (person == null || person.getDocumentNumber() == null) {
            return null;
        }
        String type = person.getDocumentType();
        return type == null || type.isBlank() ? person.getDocumentNumber() : type + "-" + person.getDocumentNumber();
    }

    private static String nameOf(Member m) {
        Person person = m.getPerson();
        return person != null && person.getFullName() != null ? person.getFullName() : "";
    }

    /** The computed split: the writes to perform and the summary to return. */
    private record Split(List<Map.Entry<Member, Promoter>> moves, MemberDistributionResultDto result) {}
}
