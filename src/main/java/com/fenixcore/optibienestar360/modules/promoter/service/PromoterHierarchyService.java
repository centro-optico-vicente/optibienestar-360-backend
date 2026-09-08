package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterHierarchyNodeDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterSupervisorAssignmentDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterSupervisorAssignment;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterSupervisorAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the promoter hierarchy (V101, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §1). Every
 * comparison in here reads {@code PromoterRank.hierarchyLevel} — never a
 * hardcoded rank name — so the chain supports any number of levels without
 * further code changes.
 *
 * <p>"Vigente at a cut" resolution ({@link #resolveSupervisorAt}) is what
 * lets a commission calculation for January always use January's supervisor
 * even after the promoter has since moved to a different one — the same
 * insert-only-history convention {@code MemberPromoterAssignment} (V35)
 * already established.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoterHierarchyService {

    /**
     * Generous upper bound on team size purely as a cycle/runaway-recursion
     * guard for {@link #resolveTeamMemberIds} — any real sales org is orders
     * of magnitude smaller. Hitting it means a data bug (a cycle slipped past
     * {@link #assignSupervisor}'s own check), not a legitimately huge team.
     */
    private static final int MAX_TEAM_TRAVERSAL = 5_000;

    private final PromoterRepository promoterRepository;
    private final PromoterSupervisorAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    /**
     * The supervisor vigente for {@code promoterId} as of {@code asOf} — the
     * most recent {@link PromoterSupervisorAssignment} row with {@code
     * createdAt <= asOf}. {@code null} means either "no supervisor" or "no
     * assignment had happened yet by that point in time" — both read the
     * same to a commission cut (nobody to cascade to).
     */
    public Promoter resolveSupervisorAt(Long promoterId, Instant asOf) {
        return assignmentRepository
                .findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(promoterId, asOf)
                .map(PromoterSupervisorAssignment::getToSupervisor)
                .orElse(null);
    }

    /**
     * Direct subordinates of {@code supervisorId} vigente as of {@code asOf}.
     * Candidates are every promoter who was <b>ever</b> linked to this
     * supervisor (current pointer or any historical assignment row on either
     * side); each candidate is then re-checked against {@link
     * #resolveSupervisorAt} for the actual {@code asOf} — necessary because a
     * past subordinate may have since moved elsewhere, and someone assigned
     * after {@code asOf} must not count for an earlier cut.
     */
    public List<Promoter> resolveDirectSubordinatesAt(Long supervisorId, Instant asOf) {
        Set<Long> candidateIds = candidatePromoterIdsFor(supervisorId);
        return candidateIds.stream()
                .map(promoterRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(candidate -> {
                    Promoter vigente = resolveSupervisorAt(candidate.getId(), asOf);
                    return vigente != null && vigente.getId().equals(supervisorId);
                })
                .toList();
    }

    private Set<Long> candidatePromoterIdsFor(Long supervisorId) {
        Set<Long> ids = new HashSet<>();
        promoterRepository.findBySupervisorId(supervisorId).forEach(p -> ids.add(p.getId()));
        ids.addAll(assignmentRepository.findDistinctPromoterIdByToSupervisorId(supervisorId));
        ids.addAll(assignmentRepository.findDistinctPromoterIdByFromSupervisorId(supervisorId));
        return ids;
    }

    /**
     * Every promoter in {@code promoterId}'s subtree (BFS, cycle-safe) as of
     * {@code asOf} — the "team volume" the hierarchy override tiers (§2)
     * measure. Does not include {@code promoterId} itself.
     */
    public Set<Long> resolveTeamMemberIds(Long promoterId, Instant asOf) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(promoterId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            if (visited.size() > MAX_TEAM_TRAVERSAL) {
                throw new IllegalStateException("promoter_hierarchy.cycle_detected");
            }
            resolveDirectSubordinatesAt(current, asOf).forEach(sub -> queue.add(sub.getId()));
        }
        visited.remove(promoterId);
        return visited;
    }

    /**
     * The full hierarchy as a tree, rooted at every promoter with no
     * supervisor (top of their own chain) as of {@code asOf} — powers {@code
     * GET /v1/admin/promoters/hierarchy-tree} for a Nuxt org-chart view.
     * Excludes the seeded {@code INSTITUCION} system row and inactive
     * promoters (a deactivated promoter's still-active subordinates are kept
     * — they're real, just orphaned under a disabled node — so nothing
     * silently disappears from the tree).
     *
     * <p>Cycle-safe the same way {@link #resolveTeamMemberIds} is: a shared
     * {@code visited} set across the whole traversal means a data bug (a
     * cycle that slipped past {@link #assignSupervisor}'s own check) simply
     * truncates that branch rather than recursing forever.</p>
     */
    public List<PromoterHierarchyNodeDto> buildTree(Instant asOf) {
        List<Promoter> roots = promoterRepository.findBySupervisorIsNull().stream()
                .filter(Promoter::isActive)
                .filter(p -> !p.isSystem())
                .sorted(Comparator.comparing(Promoter::getDisplayName))
                .toList();
        Set<Long> visited = new HashSet<>();
        return roots.stream().map(root -> toNode(root, asOf, visited)).toList();
    }

    private PromoterHierarchyNodeDto toNode(Promoter promoter, Instant asOf, Set<Long> visited) {
        List<PromoterHierarchyNodeDto> children = visited.add(promoter.getId())
                ? resolveDirectSubordinatesAt(promoter.getId(), asOf).stream()
                        .filter(Promoter::isActive)
                        .sorted(Comparator.comparing(Promoter::getDisplayName))
                        .map(child -> toNode(child, asOf, visited))
                        .toList()
                : List.of(); // cycle guard — already visited, stop this branch here

        var rank = promoter.getRank();
        var supervisor = promoter.getSupervisor();
        return new PromoterHierarchyNodeDto(
                promoter.getUuid(),
                promoter.getDisplayName(),
                promoter.getReferralCode(),
                rank != null ? rank.getCode() : null,
                rank != null ? rank.getName() : null,
                supervisor != null ? supervisor.getUuid() : null,
                children);
    }

    /** Reassignment history of a promoter's supervisor, newest first. */
    public List<PromoterSupervisorAssignmentDto> history(UUID promoterUuid) {
        if (!promoterRepository.findByUuid(promoterUuid).isPresent()) {
            throw new NoSuchElementException("promoter.not_found");
        }
        return assignmentRepository.findByPromoter_UuidOrderByCreatedAtDesc(promoterUuid).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * (Re)assigns {@code promoter}'s supervisor. {@code supervisorUuid = null}
     * clears it (promoter becomes top of its own chain). Validates:
     * <ul>
     *   <li>the candidate is not the promoter itself;</li>
     *   <li>the candidate's rank is strictly above the promoter's (pregunta 8
     *       of the hub notes — an ascension that breaks this must be fixed
     *       here, at reassignment time, not silently allowed);</li>
     *   <li>the candidate is not already inside the promoter's own subtree
     *       (would create a cycle);</li>
     *   <li>the candidate has capacity left under {@code
     *       PromoterRank.maxSubordinates} (skipped when {@code null} = no cap).</li>
     * </ul>
     */
    @Transactional
    @Auditable(entity = "promoter_supervisor", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PromoterSupervisorAssignmentDto assignSupervisor(UUID promoterUuid, UUID supervisorUuid,
                                                            String reason, UUID actorUserUuid) {
        Promoter promoter = promoterRepository.findByUuid(promoterUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        Promoter target = supervisorUuid == null ? null
                : promoterRepository.findByUuid(supervisorUuid)
                        .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));

        if (target != null) {
            if (target.getId().equals(promoter.getId())) {
                throw new IllegalArgumentException("promoter_hierarchy.self_supervision");
            }
            validateRankAbove(promoter, target);
            validateNoCycle(promoter, target);
            validateCapacity(target);
        }

        Promoter from = promoter.getSupervisor();
        Long fromId = from != null ? from.getId() : null;
        Long targetId = target != null ? target.getId() : null;
        if (Objects.equals(fromId, targetId)) {
            throw new IllegalArgumentException("promoter_hierarchy.unchanged");
        }

        User actor = actorUserUuid == null ? null
                : userRepository.findByUuid(actorUserUuid).orElse(null);

        promoter.setSupervisor(target);  // managed → dirty-check flushes on commit

        PromoterSupervisorAssignment record = new PromoterSupervisorAssignment();
        record.setPromoter(promoter);
        record.setFromSupervisor(from);
        record.setToSupervisor(target);
        record.setActor(actor);
        record.setReason(reason);
        PromoterSupervisorAssignment saved = assignmentRepository.save(record);

        return toDto(saved);
    }

    private void validateRankAbove(Promoter subordinate, Promoter candidateSupervisor) {
        int subordinateLevel = subordinate.getRank().getHierarchyLevel();
        int supervisorLevel = candidateSupervisor.getRank().getHierarchyLevel();
        if (supervisorLevel <= subordinateLevel) {
            throw new IllegalArgumentException("promoter_hierarchy.supervisor_rank_not_higher");
        }
    }

    private void validateNoCycle(Promoter subordinate, Promoter candidateSupervisor) {
        Set<Long> subordinateTeam = resolveTeamMemberIds(subordinate.getId(), Instant.now());
        if (subordinateTeam.contains(candidateSupervisor.getId())) {
            throw new IllegalArgumentException("promoter_hierarchy.cycle_detected");
        }
    }

    private void validateCapacity(Promoter candidateSupervisor) {
        Integer cap = candidateSupervisor.getRank().getMaxSubordinates();
        if (cap == null) {
            return;
        }
        long current = resolveDirectSubordinatesAt(candidateSupervisor.getId(), Instant.now()).size();
        if (current >= cap) {
            throw new IllegalArgumentException("promoter_hierarchy.max_subordinates_exceeded");
        }
    }

    private PromoterSupervisorAssignmentDto toDto(PromoterSupervisorAssignment a) {
        Promoter from = a.getFromSupervisor();
        Promoter to = a.getToSupervisor();
        User actor = a.getActor();
        return new PromoterSupervisorAssignmentDto(
                a.getUuid(),
                a.getPromoter().getUuid(),
                a.getPromoter().getDisplayName(),
                from != null ? from.getUuid() : null,
                from != null ? from.getDisplayName() : null,
                to != null ? to.getUuid() : null,
                to != null ? to.getDisplayName() : null,
                actor != null ? actor.getUuid() : null,
                a.getReason(),
                a.getCreatedAt());
    }
}
