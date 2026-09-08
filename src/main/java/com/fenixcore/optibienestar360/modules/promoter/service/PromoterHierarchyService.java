package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterHierarchyNodeDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterSupervisorAssignmentDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterSupervisorAssignment;
import com.fenixcore.optibienestar360.modules.promoter.mapper.PromoterMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRankRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterSupervisorAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PromoterHierarchyService {

    /**
     * Generous upper bound on team size purely as a cycle/runaway-recursion
     * guard for {@link #resolveTeamMemberIds} — any real sales org is orders
     * of magnitude smaller. Hitting it means a data bug (a cycle slipped past
     * {@link #assignSupervisor}'s own check), not a legitimately huge team.
     */
    private static final int MAX_TEAM_TRAVERSAL = 5_000;

    private final PromoterRepository promoterRepository;
    private final PromoterRankRepository promoterRankRepository;
    private final PromoterSupervisorAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final PromoterMapper promoterMapper;

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

    /**
     * Candidates for {@code newSupervisorUuid} in {@link #changeRank}. By
     * default ({@code allSuperiors = false}) scoped to just the <b>immediate</b>
     * next rank above {@code targetRankUuid} (e.g. targeting PROMOTOR lists
     * only active SUPERVISOR promoters, not COORDINADOR ones too) — the
     * common case, since {@code changeRank} itself still accepts any
     * strictly-higher rank if the admin insists on skipping a level.
     * {@code allSuperiors = true} widens the listing to every rank above,
     * not just the immediate one.
     *
     * <p>An empty result has two different meanings the client must tell
     * apart itself (both render the same way here): {@code targetRankUuid}
     * is the top rank (no supervisor applies, hide/disable the field), or it
     * isn't but nobody currently holds a qualifying higher rank yet (no
     * supervisor available — block submission until one exists).</p>
     */
    public List<OptionDto> eligibleSupervisorOptions(UUID targetRankUuid, boolean allSuperiors, String q, int limit) {
        PromoterRank targetRank = promoterRankRepository.findByUuid(targetRankUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter_rank.not_found"));

        Integer maxLevel = null;
        if (!allSuperiors) {
            PromoterRank immediateSuperior = promoterRankRepository
                    .findFirstByHierarchyLevelGreaterThanAndActiveTrueOrderByHierarchyLevelAsc(targetRank.getHierarchyLevel())
                    .orElse(null);
            if (immediateSuperior == null) {
                return List.of(); // top rank — no supervisor possible regardless of allSuperiors
            }
            maxLevel = immediateSuperior.getHierarchyLevel();
        }

        int cappedLimit = Math.max(1, Math.min(limit, 200));
        return promoterRepository.findEligibleSupervisors(targetRank.getHierarchyLevel(), maxLevel, q).stream()
                .limit(cappedLimit)
                .map(p -> new OptionDto(p.getUuid(), p.getReferralCode(),
                        p.getDisplayName() + " — " + p.getRank().getCode(), p.isActive()))
                .toList();
    }

    /**
     * Ascends or demotes {@code promoter} to {@code newRankUuid}, changing
     * their supervisor in the same action so the promoter is never left in
     * an invalid state (rank without a strictly-higher supervisor, unless
     * {@code newRankUuid} is the top rank). Validates, beyond {@link
     * #assignSupervisor}'s own rules (self-supervision, cycle, capacity —
     * checked here against the <b>new</b> rank, not the promoter's current
     * one):
     * <ul>
     *   <li>{@code newSupervisorUuid} is required unless {@code newRankUuid}
     *       has no active rank above it (top of the chain);</li>
     *   <li>every one of the promoter's <b>current</b> direct subordinates
     *       must still have a rank strictly below {@code newRankUuid} — a
     *       demotion that would leave a subordinate at or above their own
     *       new rank is rejected; the admin must reassign those subordinates
     *       first.</li>
     * </ul>
     * A {@link PromoterSupervisorAssignment} history row is written only
     * when the resolved supervisor actually changes — keeping the same boss
     * across a rank change (e.g. demoting a Coordinador back to Supervisor
     * under the same Coordinador above them) is valid and silent on that
     * side.
     */
    @Transactional
    @Auditable(entity = "promoter", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PromoterDto changeRank(UUID promoterUuid, UUID newRankUuid, UUID newSupervisorUuid,
                                  String reason, UUID actorUserUuid) {
        Promoter promoter = promoterRepository.findByUuid(promoterUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        PromoterRank newRank = promoterRankRepository.findByUuid(newRankUuid)
                .orElseThrow(() -> new NoSuchElementException("promoter_rank.not_found"));
        Promoter newSupervisor = newSupervisorUuid == null ? null
                : promoterRepository.findByUuid(newSupervisorUuid)
                        .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));

        if (newSupervisor == null) {
            if (promoterRankRepository.existsByHierarchyLevelGreaterThanAndActiveTrue(newRank.getHierarchyLevel())) {
                throw new IllegalArgumentException("promoter_hierarchy.rank_change.supervisor_required");
            }
        } else {
            if (newSupervisor.getId().equals(promoter.getId())) {
                throw new IllegalArgumentException("promoter_hierarchy.self_supervision");
            }
            if (newSupervisor.getRank() == null
                    || newSupervisor.getRank().getHierarchyLevel() <= newRank.getHierarchyLevel()) {
                throw new IllegalArgumentException("promoter_hierarchy.supervisor_rank_not_higher");
            }
            if (resolveTeamMemberIds(promoter.getId(), Instant.now()).contains(newSupervisor.getId())) {
                throw new IllegalArgumentException("promoter_hierarchy.cycle_detected");
            }
            Integer cap = newSupervisor.getRank().getMaxSubordinates();
            if (cap != null) {
                Promoter currentSupervisor = promoter.getSupervisor();
                boolean alreadyCounted = currentSupervisor != null && currentSupervisor.getId().equals(newSupervisor.getId());
                long current = resolveDirectSubordinatesAt(newSupervisor.getId(), Instant.now()).size();
                if (!alreadyCounted && current >= cap) {
                    throw new IllegalArgumentException("promoter_hierarchy.max_subordinates_exceeded");
                }
            }
        }

        for (Promoter subordinate : resolveDirectSubordinatesAt(promoter.getId(), Instant.now())) {
            if (subordinate.getRank() == null || subordinate.getRank().getHierarchyLevel() >= newRank.getHierarchyLevel()) {
                throw new IllegalArgumentException("promoter_hierarchy.rank_change.subordinate_rank_conflict");
            }
        }

        promoter.setRank(newRank);

        Promoter fromSupervisor = promoter.getSupervisor();
        Long fromSupervisorId = fromSupervisor != null ? fromSupervisor.getId() : null;
        Long newSupervisorId = newSupervisor != null ? newSupervisor.getId() : null;
        if (!Objects.equals(fromSupervisorId, newSupervisorId)) {
            User actor = actorUserUuid == null ? null : userRepository.findByUuid(actorUserUuid).orElse(null);
            promoter.setSupervisor(newSupervisor);
            PromoterSupervisorAssignment record = new PromoterSupervisorAssignment();
            record.setPromoter(promoter);
            record.setFromSupervisor(fromSupervisor);
            record.setToSupervisor(newSupervisor);
            record.setActor(actor);
            record.setReason(reason);
            assignmentRepository.save(record);
        }

        log.info("Promoter rank changed: promoter={} newRank={} newSupervisor={}",
                promoter.getReferralCode(), newRank.getCode(),
                newSupervisor != null ? newSupervisor.getReferralCode() : "(none)");

        return promoterMapper.toDto(promoter);
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
