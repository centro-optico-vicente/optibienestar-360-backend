package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateFilter;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCandidateSource;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionMode;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionRequest.Target;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDistributionResultDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MemberPortfolioDistributionService} (ADR LOC-0016): candidates paging by source, the EQUAL
 * round-robin and the COUNT split, the skip of members already on their target, the validation
 * (422 codes) and that the preview — and any failed distribution — writes nothing.
 */
@ExtendWith(MockitoExtension.class)
class MemberPortfolioDistributionServiceTest {

    private static final AtomicLong IDS = new AtomicLong(100);

    @Mock private MemberRepository memberRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberPromoterService memberPromoterService;

    @InjectMocks
    private MemberPortfolioDistributionService service;

    // ─── EQUAL ──────────────────────────────────────────────────────────────

    @Test
    void equal_tenMembersOverThreeAdvisors_isFourThreeThree() {
        List<Promoter> targets = List.of(advisor("A"), advisor("B"), advisor("C"));
        List<Member> members = members(10, null);

        MemberDistributionResultDto result = service.preview(byUuids(members, targets, MemberDistributionMode.EQUAL));

        assertThat(result.total()).isEqualTo(10);
        assertThat(result.skipped()).isZero();
        assertThat(result.targets()).extracting(MemberDistributionResultDto.TargetResult::count)
                .containsExactly(4, 3, 3);
        assertThat(result.targets()).extracting(MemberDistributionResultDto.TargetResult::promoterDisplay)
                .containsExactly("A", "B", "C");
    }

    @Test
    void preview_writesNothing() {
        List<Promoter> targets = List.of(advisor("A"), advisor("B"));
        List<Member> members = members(4, null);

        service.preview(byUuids(members, targets, MemberDistributionMode.EQUAL));

        verify(memberPromoterService, never()).reassignAll(any(), anyString(), any());
        assertThat(members).allSatisfy(m -> assertThat(m.getPromoter()).isNull());
    }

    @Test
    void distribute_equal_dealsRoundRobinInNameOrder_inOneWrite_withTheTrimmedReason() {
        Promoter a = advisor("A");
        Promoter b = advisor("B");
        List<Member> members = members(3, null);
        List<Member> shuffled = new ArrayList<>(members);
        Collections.reverse(shuffled);  // request order must not matter
        UUID actor = UUID.randomUUID();

        MemberDistributionResultDto result = service.distribute(new MemberDistributionRequest(
                shuffled.stream().map(Member::getUuid).toList(), null, null,
                targetsOf(List.of(a, b), null), MemberDistributionMode.EQUAL, "  Rotación  "), actor);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map.Entry<Member, Promoter>>> moves = ArgumentCaptor.forClass(List.class);
        verify(memberPromoterService).reassignAll(moves.capture(), eq("Rotación"), eq(actor));
        assertThat(moves.getValue()).extracting(Map.Entry::getKey).containsExactlyElementsOf(members);
        assertThat(moves.getValue()).extracting(Map.Entry::getValue).containsExactly(a, b, a);
        assertThat(result.total()).isEqualTo(3);
    }

    @Test
    void membersAlreadyOnTheirTarget_areSkipped() {
        Promoter a = advisor("A");
        Promoter b = advisor("B");
        List<Member> members = members(4, null);
        members.get(0).setPromoter(a);  // dealt to A → skipped
        members.get(1).setPromoter(a);  // dealt to B → moved

        MemberDistributionResultDto result = service.preview(byUuids(members, List.of(a, b), MemberDistributionMode.EQUAL));

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.targets()).extracting(MemberDistributionResultDto.TargetResult::count).containsExactly(1, 2);
    }

    // ─── COUNT ──────────────────────────────────────────────────────────────

    @Test
    void count_dealsTheExactCounts_andLeavesTheRestUntouched() {
        Promoter a = advisor("A");
        Promoter b = advisor("B");
        List<Member> members = members(5, null);

        MemberDistributionResultDto result = service.preview(new MemberDistributionRequest(
                uuidsOf(members), null, null, List.of(new Target(a.getUuid(), 2), new Target(b.getUuid(), 1)),
                MemberDistributionMode.COUNT, "Rotación"));

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.targets()).extracting(MemberDistributionResultDto.TargetResult::count).containsExactly(2, 1);
    }

    @Test
    void count_withoutACountPerTarget_is422() {
        Promoter a = advisor("A");

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(
                List.of(UUID.randomUUID()), null, null, List.of(new Target(a.getUuid(), null)),
                MemberDistributionMode.COUNT, "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member.distribution.count_required");
    }

    @Test
    void count_zero_is422() {
        Promoter a = advisor("A");

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(
                List.of(UUID.randomUUID()), null, null, List.of(new Target(a.getUuid(), 0)),
                MemberDistributionMode.COUNT, "x")))
                .hasMessage("member.distribution.count_required");
    }

    @Test
    void count_sumOverTheSelection_is422_andWritesNothing() {
        Promoter a = advisor("A");
        Promoter b = advisor("B");
        List<Member> members = members(3, null);

        assertThatThrownBy(() -> service.distribute(new MemberDistributionRequest(
                uuidsOf(members), null, null, List.of(new Target(a.getUuid(), 2), new Target(b.getUuid(), 2)),
                MemberDistributionMode.COUNT, "x"), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member.distribution.count_exceeds");
        verify(memberPromoterService, never()).reassignAll(any(), anyString(), any());
    }

    // ─── Targets ────────────────────────────────────────────────────────────

    @Test
    void systemTarget_is422() {
        Promoter institucion = advisor("INSTITUCION");
        institucion.setSystem(true);

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(List.of(UUID.randomUUID()), null, null,
                targetsOf(List.of(institucion), null), MemberDistributionMode.EQUAL, "x")))
                .hasMessage("promoter.system_not_assignable");
    }

    @Test
    void inactiveTarget_is422() {
        Promoter gone = advisor("GONE");
        gone.setActive(false);

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(List.of(UUID.randomUUID()), null, null,
                targetsOf(List.of(gone), null), MemberDistributionMode.EQUAL, "x")))
                .hasMessage("promoter.inactive");
    }

    @Test
    void duplicateTargets_are422() {
        UUID same = UUID.randomUUID();
        when(promoterRepository.findByUuid(same)).thenReturn(Optional.of(advisor("A")));

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(List.of(UUID.randomUUID()), null, null,
                List.of(new Target(same, null), new Target(same, null)), MemberDistributionMode.EQUAL, "x")))
                .hasMessage("member.distribution.targets_duplicate");
    }

    @Test
    void moreThanTwentyTargets_or_none_are422() {
        List<Target> many = IntStream.range(0, 21).mapToObj(i -> new Target(UUID.randomUUID(), null)).toList();

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(List.of(UUID.randomUUID()), null, null,
                many, MemberDistributionMode.EQUAL, "x")))
                .hasMessage("member.distribution.targets_invalid");
        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(List.of(UUID.randomUUID()), null, null,
                List.of(), MemberDistributionMode.EQUAL, "x")))
                .hasMessage("member.distribution.targets_invalid");
    }

    // ─── Selection ──────────────────────────────────────────────────────────

    @Test
    void bothOrNeitherSelection_is422() {
        Promoter a = advisor("A");
        MemberCandidateFilter filter = new MemberCandidateFilter(MemberCandidateSource.UNASSIGNED, null, null, null, null);

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(List.of(UUID.randomUUID()), filter, null,
                targetsOf(List.of(a), null), MemberDistributionMode.EQUAL, "x")))
                .hasMessage("member.distribution.selection_invalid");
        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(null, null, null,
                targetsOf(List.of(a), null), MemberDistributionMode.EQUAL, "x")))
                .hasMessage("member.distribution.selection_invalid");
    }

    @Test
    void moreThan2000Members_is422_beforeLoadingThem() {
        Promoter a = advisor("A");
        List<UUID> uuids = IntStream.range(0, 2001).mapToObj(i -> UUID.randomUUID()).toList();

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(uuids, null, null,
                targetsOf(List.of(a), null), MemberDistributionMode.EQUAL, "x")))
                .hasMessage("member.distribution.too_many");
        verify(memberRepository, never()).findByUuid(any());
    }

    @Test
    void anUnknownMember_failsTheWholeDistribution_andWritesNothing() {
        Promoter a = advisor("A");
        List<Member> members = members(2, null);
        UUID missing = UUID.randomUUID();
        when(memberRepository.findByUuid(missing)).thenReturn(Optional.empty());
        List<UUID> uuids = new ArrayList<>(uuidsOf(members));
        uuids.add(missing);

        assertThatThrownBy(() -> service.distribute(new MemberDistributionRequest(uuids, null, null,
                targetsOf(List.of(a), null), MemberDistributionMode.EQUAL, "x"), UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("member.not_found");
        verify(memberPromoterService, never()).reassignAll(any(), anyString(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void filterSelection_takesEveryResult_minusTheExcluded() {
        Promoter source = advisor("SALIENTE");
        source.setActive(false);  // a departed advisor is a valid source
        Promoter a = advisor("A");
        List<Member> members = members(5, source);
        when(promoterRepository.findByUuid(source.getUuid())).thenReturn(Optional.of(source));
        when(memberRepository.count(any(Specification.class))).thenReturn(5L);
        when(memberRepository.findAll(any(Specification.class))).thenReturn(members);

        MemberDistributionResultDto result = service.preview(new MemberDistributionRequest(null,
                new MemberCandidateFilter(MemberCandidateSource.ADVISOR, source.getUuid(), null, null, null),
                List.of(members.get(0).getUuid()), targetsOf(List.of(a), null), MemberDistributionMode.EQUAL, "Baja"));

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.targets().get(0).count()).isEqualTo(4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void filterSelection_overTheCap_is422_withoutLoadingTheMembers() {
        Promoter a = advisor("A");
        when(memberRepository.count(any(Specification.class))).thenReturn(2500L);

        assertThatThrownBy(() -> service.preview(new MemberDistributionRequest(null,
                new MemberCandidateFilter(MemberCandidateSource.UNASSIGNED, null, null, null, null),
                null, targetsOf(List.of(a), null), MemberDistributionMode.EQUAL, "x")))
                .hasMessage("member.distribution.too_many");
        verify(memberRepository, never()).findAll(any(Specification.class));
    }

    // ─── Candidates ─────────────────────────────────────────────────────────

    @Test
    void candidates_advisorSource_withoutTheAdvisor_is422() {
        assertThatThrownBy(() -> service.candidates(
                new MemberCandidateFilter(MemberCandidateSource.ADVISOR, null, null, null, null), PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member.candidates.source_promoter_required");
    }

    @Test
    void candidates_unknownSourceAdvisor_is404() {
        UUID unknown = UUID.randomUUID();
        when(promoterRepository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.candidates(
                new MemberCandidateFilter(MemberCandidateSource.ADVISOR, unknown, null, null, null), PageRequest.of(0, 20)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("promoter.not_found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void candidates_unassignedSource_pagesByNameThenUuid_andMapsPlanAdvisorAndDocument() {
        Promoter institucion = advisor("INSTITUCION");
        institucion.setSystem(true);
        Member withSystem = members(1, institucion).get(0);
        Member withoutAdvisor = members(1, null).get(0);
        Plan plan = new Plan();
        plan.setUuid(UUID.randomUUID());
        plan.setName("Plan Familiar");
        Membership membership = new Membership();
        membership.setMember(withSystem);
        membership.setPlan(plan);
        membership.setStatus("SUSPENDED");
        withoutAdvisor.setStatus("SUSPENDED");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        Page<Member> page = new PageImpl<>(List.of(withSystem, withoutAdvisor), PageRequest.of(1, 2), 7);
        when(memberRepository.findAll(any(Specification.class), pageable.capture())).thenReturn(page);
        when(membershipRepository.findActiveWithPlanByMemberIds(any())).thenReturn(List.of(membership));

        Page<MemberCandidateDto> result = service.candidates(
                new MemberCandidateFilter(MemberCandidateSource.UNASSIGNED, null, "per", null, null), PageRequest.of(1, 2));

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getSort()).extracting(Sort.Order::getProperty)
                .containsExactly("person.fullName", "uuid");
        assertThat(result.getTotalElements()).isEqualTo(7);
        MemberCandidateDto first = result.getContent().get(0);
        assertThat(first.document()).isEqualTo("V-" + withSystem.getPerson().getDocumentNumber());
        assertThat(first.plan().name()).isEqualTo("Plan Familiar");
        assertThat(first.status()).isEqualTo("ACTIVE");  // the member's status, not the membership's
        assertThat(first.promoter().name()).isEqualTo("INSTITUCION");
        MemberCandidateDto second = result.getContent().get(1);
        assertThat(second.promoter()).isNull();
        assertThat(second.plan()).isNull();
        assertThat(second.status()).isEqualTo("SUSPENDED");
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private MemberDistributionRequest byUuids(List<Member> members, List<Promoter> targets, MemberDistributionMode mode) {
        return new MemberDistributionRequest(uuidsOf(members), null, null, targetsOf(targets, null), mode, "Rotación");
    }

    private static List<UUID> uuidsOf(List<Member> members) {
        return members.stream().map(Member::getUuid).toList();
    }

    private static List<Target> targetsOf(List<Promoter> promoters, Integer count) {
        return promoters.stream().map(p -> new Target(p.getUuid(), count)).toList();
    }

    /** {@code n} active members named "Afiliado 00".. in order, stubbed for lookup by uuid. */
    private List<Member> members(int n, Promoter promoter) {
        List<Member> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Person person = new Person();
            person.setId(IDS.incrementAndGet());
            person.setUuid(UUID.randomUUID());
            person.setFullName("Afiliado %02d".formatted(i));
            person.setDocumentType("V");
            person.setDocumentNumber(String.valueOf(10_000_000 + i));
            Member m = new Member();
            m.setId(IDS.incrementAndGet());
            m.setUuid(UUID.randomUUID());
            m.setPerson(person);
            m.setPromoter(promoter);
            m.setActive(true);
            m.setCreatedAt(Instant.parse("2026-10-01T12:00:00Z"));
            org.mockito.Mockito.lenient().when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
            list.add(m);
        }
        return list;
    }

    /** An active, non-system advisor stubbed for lookup by uuid. */
    private Promoter advisor(String name) {
        Promoter p = new Promoter();
        p.setId(IDS.incrementAndGet());
        p.setUuid(UUID.randomUUID());
        p.setReferralCode(name);
        p.setDisplayName(name);
        p.setActive(true);
        org.mockito.Mockito.lenient().when(promoterRepository.findByUuid(p.getUuid())).thenReturn(Optional.of(p));
        return p;
    }
}
