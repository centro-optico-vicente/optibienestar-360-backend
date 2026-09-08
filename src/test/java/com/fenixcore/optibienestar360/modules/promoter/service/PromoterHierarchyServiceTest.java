package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterSupervisorAssignmentDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterSupervisorAssignment;
import com.fenixcore.optibienestar360.modules.promoter.mapper.PromoterMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRankRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterSupervisorAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromoterHierarchyService} (V101, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §1) — the
 * validation rules {@code assignSupervisor} must enforce: no self-supervision,
 * candidate rank strictly above the subordinate's, no cycles, and capacity
 * per {@code PromoterRank.maxSubordinates}.
 */
@ExtendWith(MockitoExtension.class)
class PromoterHierarchyServiceTest {

    @Mock private PromoterRepository promoterRepository;
    @Mock private PromoterRankRepository promoterRankRepository;
    @Mock private PromoterSupervisorAssignmentRepository assignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private PromoterMapper promoterMapper;

    /** Built per-call — {@code @Mock} fields are injected after construction (see CurrencyServiceTest). */
    private PromoterHierarchyService service() {
        return new PromoterHierarchyService(promoterRepository, promoterRankRepository, assignmentRepository,
                userRepository, promoterMapper);
    }

    private static PromoterRank rank(String code, int level, Integer maxSubordinates) {
        PromoterRank r = new PromoterRank();
        r.setUuid(UUID.randomUUID());
        r.setCode(code);
        r.setName(code);
        r.setHierarchyLevel(level);
        r.setMaxSubordinates(maxSubordinates);
        return r;
    }

    private static Promoter promoter(Long id, String displayName, PromoterRank rank) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setDisplayName(displayName);
        p.setRank(rank);
        return p;
    }

    /** No candidates from any source — an empty team/subtree for whichever id is queried. */
    private void stubNoExistingLinks() {
        lenient().when(promoterRepository.findBySupervisorId(any())).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByToSupervisorId(any())).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByFromSupervisorId(any())).thenReturn(List.of());
    }

    @Test
    void assignSupervisorSavesLivePointerAndAuditRow() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2, 10);
        Promoter subordinate = promoter(1L, "Ana", promotorRank);
        Promoter supervisor = promoter(2L, "Beto", supervisorRank);

        when(promoterRepository.findByUuid(subordinate.getUuid())).thenReturn(Optional.of(subordinate));
        when(promoterRepository.findByUuid(supervisor.getUuid())).thenReturn(Optional.of(supervisor));
        stubNoExistingLinks();
        when(assignmentRepository.save(any(PromoterSupervisorAssignment.class))).thenAnswer(inv -> {
            PromoterSupervisorAssignment saved = inv.getArgument(0);
            saved.setUuid(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        PromoterSupervisorAssignmentDto result = service().assignSupervisor(
                subordinate.getUuid(), supervisor.getUuid(), "Reorganización de equipo", null);

        assertThat(subordinate.getSupervisor()).isSameAs(supervisor);
        assertThat(result.toSupervisorUuid()).isEqualTo(supervisor.getUuid());
        assertThat(result.fromSupervisorUuid()).isNull();
    }

    @Test
    void assignSupervisorRejectsSelfSupervision() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        Promoter p = promoter(1L, "Ana", promotorRank);
        when(promoterRepository.findByUuid(p.getUuid())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service().assignSupervisor(p.getUuid(), p.getUuid(), "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("self_supervision");
    }

    @Test
    void assignSupervisorRejectsWhenCandidateRankNotHigher() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        Promoter subordinate = promoter(1L, "Ana", promotorRank);
        Promoter samelevel = promoter(2L, "Beto", promotorRank);

        when(promoterRepository.findByUuid(subordinate.getUuid())).thenReturn(Optional.of(subordinate));
        when(promoterRepository.findByUuid(samelevel.getUuid())).thenReturn(Optional.of(samelevel));

        assertThatThrownBy(() -> service().assignSupervisor(subordinate.getUuid(), samelevel.getUuid(), "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supervisor_rank_not_higher");
    }

    @Test
    void assignSupervisorRejectsCycle() {
        // Coordinador (id=3) is already inside Promotor(id=1)'s own subtree
        // (id=1 supervises id=2, id=2 supervises id=3) — making id=3 the
        // supervisor of id=1 would close the loop.
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2, 10);
        PromoterRank coordRank = rank("COORDINADOR", 3, 5);

        Promoter p1 = promoter(1L, "P1", promotorRank);
        Promoter p2 = promoter(2L, "P2", supervisorRank);
        Promoter p3 = promoter(3L, "P3", coordRank);

        when(promoterRepository.findByUuid(p1.getUuid())).thenReturn(Optional.of(p1));
        when(promoterRepository.findByUuid(p3.getUuid())).thenReturn(Optional.of(p3));

        // Direct-subordinate resolution for p1 → [p2]; for p2 → [p3]; for p3 → [].
        lenient().when(promoterRepository.findBySupervisorId(1L)).thenReturn(List.of(p2));
        lenient().when(promoterRepository.findBySupervisorId(2L)).thenReturn(List.of(p3));
        lenient().when(promoterRepository.findBySupervisorId(3L)).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByToSupervisorId(any())).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByFromSupervisorId(any())).thenReturn(List.of());
        lenient().when(promoterRepository.findById(2L)).thenReturn(Optional.of(p2));
        lenient().when(promoterRepository.findById(3L)).thenReturn(Optional.of(p3));

        // "vigente" resolution: p2's supervisor is p1, p3's supervisor is p2.
        PromoterSupervisorAssignment a2 = new PromoterSupervisorAssignment();
        a2.setPromoter(p2);
        a2.setToSupervisor(p1);
        PromoterSupervisorAssignment a3 = new PromoterSupervisorAssignment();
        a3.setPromoter(p3);
        a3.setToSupervisor(p2);
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(2L), any()))
                .thenReturn(Optional.of(a2));
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(3L), any()))
                .thenReturn(Optional.of(a3));

        assertThatThrownBy(() -> service().assignSupervisor(p1.getUuid(), p3.getUuid(), "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle_detected");
    }

    @Test
    void assignSupervisorRejectsWhenCandidateAtCapacity() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2, 1); // cap = 1, already full

        Promoter subordinate = promoter(1L, "Ana", promotorRank);
        Promoter supervisor = promoter(2L, "Beto", supervisorRank);
        Promoter existingSubordinate = promoter(3L, "Carlos", promotorRank);

        when(promoterRepository.findByUuid(subordinate.getUuid())).thenReturn(Optional.of(subordinate));
        when(promoterRepository.findByUuid(supervisor.getUuid())).thenReturn(Optional.of(supervisor));

        // subordinate's own subtree is empty (no cycle risk)...
        lenient().when(promoterRepository.findBySupervisorId(1L)).thenReturn(List.of());
        // ...but the candidate supervisor already has 1 direct subordinate (at cap).
        lenient().when(promoterRepository.findBySupervisorId(2L)).thenReturn(List.of(existingSubordinate));
        lenient().when(assignmentRepository.findDistinctPromoterIdByToSupervisorId(any())).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByFromSupervisorId(any())).thenReturn(List.of());
        lenient().when(promoterRepository.findById(3L)).thenReturn(Optional.of(existingSubordinate));

        PromoterSupervisorAssignment existingLink = new PromoterSupervisorAssignment();
        existingLink.setPromoter(existingSubordinate);
        existingLink.setToSupervisor(supervisor);
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(3L), any()))
                .thenReturn(Optional.of(existingLink));

        assertThatThrownBy(() -> service().assignSupervisor(subordinate.getUuid(), supervisor.getUuid(), "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max_subordinates_exceeded");
    }

    @Test
    void resolveSupervisorAtReturnsVigenteAssignmentToSupervisor() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        Promoter subordinate = promoter(1L, "Ana", promotorRank);
        Promoter supervisor = promoter(2L, "Beto", rank("SUPERVISOR", 2, 10));

        PromoterSupervisorAssignment vigente = new PromoterSupervisorAssignment();
        vigente.setPromoter(subordinate);
        vigente.setToSupervisor(supervisor);

        Instant asOf = Instant.now();
        when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(1L, asOf))
                .thenReturn(Optional.of(vigente));

        Promoter result = service().resolveSupervisorAt(1L, asOf);

        assertThat(result).isSameAs(supervisor);
    }

    @Test
    void buildTreeNestsFromEachSupervisorlessRootDownToLeaves() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2, 10);
        PromoterRank coordRank = rank("COORDINADOR", 3, 5);

        Promoter coord = promoter(3L, "Coord", coordRank);
        Promoter sup = promoter(2L, "Sup", supervisorRank);
        Promoter ase = promoter(1L, "Ase", promotorRank);
        sup.setSupervisor(coord);
        ase.setSupervisor(sup);

        when(promoterRepository.findBySupervisorIsNull()).thenReturn(List.of(coord));
        lenient().when(promoterRepository.findBySupervisorId(3L)).thenReturn(List.of(sup));
        lenient().when(promoterRepository.findBySupervisorId(2L)).thenReturn(List.of(ase));
        lenient().when(promoterRepository.findBySupervisorId(1L)).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByToSupervisorId(any())).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByFromSupervisorId(any())).thenReturn(List.of());
        lenient().when(promoterRepository.findById(2L)).thenReturn(Optional.of(sup));
        lenient().when(promoterRepository.findById(1L)).thenReturn(Optional.of(ase));

        PromoterSupervisorAssignment supVigente = new PromoterSupervisorAssignment();
        supVigente.setPromoter(sup);
        supVigente.setToSupervisor(coord);
        PromoterSupervisorAssignment aseVigente = new PromoterSupervisorAssignment();
        aseVigente.setPromoter(ase);
        aseVigente.setToSupervisor(sup);
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(2L), any()))
                .thenReturn(Optional.of(supVigente));
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(Optional.of(aseVigente));

        List<com.fenixcore.optibienestar360.modules.promoter.dto.PromoterHierarchyNodeDto> tree =
                service().buildTree(Instant.now());

        assertThat(tree).hasSize(1);
        var coordNode = tree.get(0);
        assertThat(coordNode.uuid()).isEqualTo(coord.getUuid());
        assertThat(coordNode.rankCode()).isEqualTo("COORDINADOR");
        assertThat(coordNode.supervisorUuid()).isNull();
        assertThat(coordNode.children()).hasSize(1);

        var supNode = coordNode.children().get(0);
        assertThat(supNode.uuid()).isEqualTo(sup.getUuid());
        assertThat(supNode.supervisorUuid()).isEqualTo(coord.getUuid());
        assertThat(supNode.children()).hasSize(1);

        var aseNode = supNode.children().get(0);
        assertThat(aseNode.uuid()).isEqualTo(ase.getUuid());
        assertThat(aseNode.supervisorUuid()).isEqualTo(sup.getUuid());
        assertThat(aseNode.children()).isEmpty();
    }

    @Test
    void buildTreeExcludesInactiveAndSystemRoots() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        Promoter institucion = promoter(9L, "INSTITUCION", promotorRank);
        institucion.setSystem(true);
        Promoter inactiveRoot = promoter(8L, "Inactivo", promotorRank);
        inactiveRoot.setActive(false);

        when(promoterRepository.findBySupervisorIsNull()).thenReturn(List.of(institucion, inactiveRoot));

        List<com.fenixcore.optibienestar360.modules.promoter.dto.PromoterHierarchyNodeDto> tree =
                service().buildTree(Instant.now());

        assertThat(tree).isEmpty();
    }

    @Test
    void changeRankPromotesAndAssignsNewSupervisor() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2, null);
        PromoterRank coordRank = rank("COORDINADOR", 3, null);
        Promoter ase = promoter(1L, "Ana", promotorRank);
        Promoter coord = promoter(3L, "Coord", coordRank);

        when(promoterRepository.findByUuid(ase.getUuid())).thenReturn(Optional.of(ase));
        when(promoterRepository.findByUuid(coord.getUuid())).thenReturn(Optional.of(coord));
        when(promoterRankRepository.findByUuid(supervisorRank.getUuid())).thenReturn(Optional.of(supervisorRank));
        stubNoExistingLinks();
        when(assignmentRepository.save(any(PromoterSupervisorAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        service().changeRank(ase.getUuid(), supervisorRank.getUuid(), coord.getUuid(), "Ascenso por desempeño", null);

        assertThat(ase.getRank()).isSameAs(supervisorRank);
        assertThat(ase.getSupervisor()).isSameAs(coord);
        verify(assignmentRepository).save(any(PromoterSupervisorAssignment.class));
    }

    @Test
    void changeRankToTopRankAllowsNullSupervisor() {
        PromoterRank coordRank = rank("COORDINADOR", 3, null);
        Promoter coord = promoter(3L, "Coord", coordRank);

        when(promoterRepository.findByUuid(coord.getUuid())).thenReturn(Optional.of(coord));
        when(promoterRankRepository.findByUuid(coordRank.getUuid())).thenReturn(Optional.of(coordRank));
        when(promoterRankRepository.existsByHierarchyLevelGreaterThanAndActiveTrue(3)).thenReturn(false);
        stubNoExistingLinks();

        service().changeRank(coord.getUuid(), coordRank.getUuid(), null, "Ya es el rango más alto", null);

        assertThat(coord.getRank()).isSameAs(coordRank);
        assertThat(coord.getSupervisor()).isNull();
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void changeRankRejectsMissingSupervisorWhenNotTopRank() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        Promoter ase = promoter(1L, "Ana", promotorRank);

        when(promoterRepository.findByUuid(ase.getUuid())).thenReturn(Optional.of(ase));
        when(promoterRankRepository.findByUuid(promotorRank.getUuid())).thenReturn(Optional.of(promotorRank));
        when(promoterRankRepository.existsByHierarchyLevelGreaterThanAndActiveTrue(1)).thenReturn(true);

        assertThatThrownBy(() -> service().changeRank(ase.getUuid(), promotorRank.getUuid(), null, "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supervisor_required");
    }

    @Test
    void changeRankRejectsWhenDemotionWouldOutrankACurrentSubordinate() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2, null);
        PromoterRank coordRank = rank("COORDINADOR", 3, null);
        Promoter ase = promoter(2L, "Sup", supervisorRank); // currently a Supervisor
        Promoter subordinate = promoter(1L, "Ana", promotorRank);
        Promoter coord = promoter(3L, "Coord", coordRank);

        when(promoterRepository.findByUuid(ase.getUuid())).thenReturn(Optional.of(ase));
        when(promoterRepository.findByUuid(coord.getUuid())).thenReturn(Optional.of(coord));
        when(promoterRankRepository.findByUuid(promotorRank.getUuid())).thenReturn(Optional.of(promotorRank));

        // ase's current team: "subordinate" reports to ase.
        lenient().when(promoterRepository.findBySupervisorId(2L)).thenReturn(List.of(subordinate));
        lenient().when(promoterRepository.findBySupervisorId(1L)).thenReturn(List.of());
        lenient().when(promoterRepository.findBySupervisorId(3L)).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByToSupervisorId(any())).thenReturn(List.of());
        lenient().when(assignmentRepository.findDistinctPromoterIdByFromSupervisorId(any())).thenReturn(List.of());
        lenient().when(promoterRepository.findById(1L)).thenReturn(Optional.of(subordinate));
        PromoterSupervisorAssignment vigente = new PromoterSupervisorAssignment();
        vigente.setPromoter(subordinate);
        vigente.setToSupervisor(ase);
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(assignmentRepository.findFirstByPromoterIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenReturn(Optional.of(vigente));

        // Demoting ase (Supervisor) down to Promotor — same rank as its own subordinate — must be rejected,
        // even though the proposed new supervisor (a Coordinador) is otherwise perfectly valid.
        assertThatThrownBy(() -> service().changeRank(ase.getUuid(), promotorRank.getUuid(), coord.getUuid(), "x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subordinate_rank_conflict");
    }

    @Test
    void changeRankKeepingSameSupervisorDoesNotWriteAssignmentRow() {
        PromoterRank supervisorRank = rank("SUPERVISOR", 2, null);
        PromoterRank coordRank = rank("COORDINADOR", 3, null);
        Promoter sup = promoter(2L, "Sup", supervisorRank);
        Promoter coord = promoter(3L, "Coord", coordRank);
        sup.setSupervisor(coord);

        when(promoterRepository.findByUuid(sup.getUuid())).thenReturn(Optional.of(sup));
        when(promoterRepository.findByUuid(coord.getUuid())).thenReturn(Optional.of(coord));
        when(promoterRankRepository.findByUuid(supervisorRank.getUuid())).thenReturn(Optional.of(supervisorRank));
        stubNoExistingLinks();

        service().changeRank(sup.getUuid(), supervisorRank.getUuid(), coord.getUuid(), "Confirmar rango", null);

        assertThat(sup.getSupervisor()).isSameAs(coord);
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void eligibleSupervisorOptionsListsPromotersAboveTargetRank() {
        PromoterRank promotorRank = rank("PROMOTOR", 1, null);
        PromoterRank supervisorRank = rank("SUPERVISOR", 2, null);
        Promoter sup = promoter(2L, "Sup", supervisorRank);

        when(promoterRankRepository.findByUuid(promotorRank.getUuid())).thenReturn(Optional.of(promotorRank));
        when(promoterRepository.findEligibleSupervisors(1, null)).thenReturn(List.of(sup));

        List<com.fenixcore.optibienestar360.core.dto.OptionDto> options =
                service().eligibleSupervisorOptions(promotorRank.getUuid(), null, 50);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).uuid()).isEqualTo(sup.getUuid());
    }
}
