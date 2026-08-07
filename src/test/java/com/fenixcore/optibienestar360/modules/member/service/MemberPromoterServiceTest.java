package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.member.dto.MemberPromoterAssignmentDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.entity.MemberPromoterAssignment;
import com.fenixcore.optibienestar360.modules.member.repository.MemberPromoterAssignmentRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MemberPromoterService} — the permanent-link reassignment
 * (v2 PDF 2.a). Locks: the member's promoter is updated, exactly one audit row
 * is written capturing from → to + actor + reason, and the guard rails
 * (unknown member/promoter, inactive target, no-op reassignment) reject cleanly.
 */
@ExtendWith(MockitoExtension.class)
class MemberPromoterServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private UserRepository userRepository;
    @Mock private MemberPromoterAssignmentRepository assignmentRepository;

    private MemberPromoterService service;

    private MemberPromoterService service() {
        return new MemberPromoterService(
                memberRepository, promoterRepository, userRepository, assignmentRepository);
    }

    @Test
    void assign_setsPromoter_andWritesAuditRow_fromNullToTarget() {
        service = service();
        Member member = member(null);
        Promoter target = promoter("VICENTE", true);
        User actor = user();
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(promoterRepository.findByUuid(target.getUuid())).thenReturn(Optional.of(target));
        when(userRepository.findByUuid(actor.getUuid())).thenReturn(Optional.of(actor));
        when(assignmentRepository.save(any())).thenAnswer(inv -> {
            MemberPromoterAssignment a = inv.getArgument(0);
            a.setUuid(UUID.randomUUID());
            a.setCreatedAt(Instant.parse("2026-07-25T12:00:00Z"));
            return a;
        });

        MemberPromoterAssignmentDto dto =
                service.assign(member.getUuid(), target.getUuid(), "Cartera nueva", actor.getUuid());

        // The permanent link now points at the target.
        assertThat(member.getPromoter()).isSameAs(target);

        // Exactly one audit row, capturing the transition.
        ArgumentCaptor<MemberPromoterAssignment> captor =
                ArgumentCaptor.forClass(MemberPromoterAssignment.class);
        verify(assignmentRepository).save(captor.capture());
        MemberPromoterAssignment row = captor.getValue();
        assertThat(row.getMember()).isSameAs(member);
        assertThat(row.getFromPromoter()).isNull();
        assertThat(row.getToPromoter()).isSameAs(target);
        assertThat(row.getActor()).isSameAs(actor);
        assertThat(row.getReason()).isEqualTo("Cartera nueva");

        // DTO echoes the transition.
        assertThat(dto.toPromoterUuid()).isEqualTo(target.getUuid());
        assertThat(dto.fromPromoterUuid()).isNull();
        assertThat(dto.actorUserUuid()).isEqualTo(actor.getUuid());
        assertThat(dto.reason()).isEqualTo("Cartera nueva");
    }

    @Test
    void assign_recordsPreviousPromoter_onReassignment() {
        service = service();
        Promoter from = promoter("OLD", true);
        Promoter target = promoter("NEW", true);
        Member member = member(from);
        User actor = user();
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(promoterRepository.findByUuid(target.getUuid())).thenReturn(Optional.of(target));
        when(userRepository.findByUuid(actor.getUuid())).thenReturn(Optional.of(actor));
        when(assignmentRepository.save(any())).thenAnswer(inv -> {
            MemberPromoterAssignment a = inv.getArgument(0);
            a.setUuid(UUID.randomUUID());
            a.setCreatedAt(Instant.parse("2026-07-25T12:00:00Z"));
            return a;
        });

        MemberPromoterAssignmentDto dto =
                service.assign(member.getUuid(), target.getUuid(), "Traslado", actor.getUuid());

        assertThat(member.getPromoter()).isSameAs(target);
        assertThat(dto.fromPromoterUuid()).isEqualTo(from.getUuid());
        assertThat(dto.toPromoterUuid()).isEqualTo(target.getUuid());
    }

    @Test
    void assign_404_whenMemberUnknown() {
        service = service();
        UUID missing = UUID.randomUUID();
        when(memberRepository.findByUuid(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(missing, UUID.randomUUID(), "x", UUID.randomUUID()))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("member.not_found");
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_404_whenPromoterUnknown() {
        service = service();
        Member member = member(null);
        UUID promoterUuid = UUID.randomUUID();
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(promoterRepository.findByUuid(promoterUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(member.getUuid(), promoterUuid, "x", UUID.randomUUID()))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("promoter.not_found");
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_422_whenTargetPromoterInactive() {
        service = service();
        Member member = member(null);
        Promoter inactive = promoter("VICENTE", false);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(promoterRepository.findByUuid(inactive.getUuid())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.assign(member.getUuid(), inactive.getUuid(), "x", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter.inactive");
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void assign_byReferralCode_resolvesPromoter_andLinksUnlinkedMember() {
        service = service();
        Member member = member(null);
        Promoter target = promoter("PROMO01", true);
        User actor = user();
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(promoterRepository.findByReferralCode("PROMO01")).thenReturn(Optional.of(target));
        when(userRepository.findByUuid(actor.getUuid())).thenReturn(Optional.of(actor));
        when(assignmentRepository.save(any())).thenAnswer(inv -> {
            MemberPromoterAssignment a = inv.getArgument(0);
            a.setUuid(UUID.randomUUID());
            a.setCreatedAt(Instant.parse("2026-08-06T12:00:00Z"));
            return a;
        });

        MemberPromoterAssignmentDto dto =
                service.assign(member.getUuid(), null, "promo01", "Vinculación inicial", actor.getUuid());

        assertThat(member.getPromoter()).isSameAs(target);
        assertThat(dto.fromPromoterUuid()).isNull();
        assertThat(dto.toPromoterUuid()).isEqualTo(target.getUuid());
    }

    @Test
    void assign_404_whenReferralCodeUnknown() {
        service = service();
        Member member = member(null);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(promoterRepository.findByReferralCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(member.getUuid(), null, "nope", "x", UUID.randomUUID()))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("promoter.not_found");
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void history_mapsAssignments_newestFirst() {
        service = service();
        Member member = member(null);
        Promoter from = promoter("OLD", true);
        Promoter to = promoter("NEW", true);
        MemberPromoterAssignment row = new MemberPromoterAssignment();
        row.setUuid(UUID.randomUUID());
        row.setMember(member);
        row.setFromPromoter(from);
        row.setToPromoter(to);
        row.setReason("Traslado");
        row.setCreatedAt(Instant.parse("2026-08-06T12:00:00Z"));
        when(memberRepository.existsByUuid(member.getUuid())).thenReturn(true);
        when(assignmentRepository.findByMember_UuidOrderByCreatedAtDesc(member.getUuid()))
                .thenReturn(java.util.List.of(row));

        var history = service.history(member.getUuid());

        assertThat(history).hasSize(1);
        assertThat(history.get(0).fromPromoterUuid()).isEqualTo(from.getUuid());
        assertThat(history.get(0).toPromoterUuid()).isEqualTo(to.getUuid());
    }

    @Test
    void history_404_whenMemberUnknown() {
        service = service();
        UUID missing = UUID.randomUUID();
        when(memberRepository.existsByUuid(missing)).thenReturn(false);

        assertThatThrownBy(() -> service.history(missing))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("member.not_found");
    }

    @Test
    void assign_422_whenReassigningToSamePromoter() {
        service = service();
        Promoter same = promoter("VICENTE", true);
        Member member = member(same);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(promoterRepository.findByUuid(same.getUuid())).thenReturn(Optional.of(same));

        assertThatThrownBy(() -> service.assign(member.getUuid(), same.getUuid(), "x", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member.promoter.unchanged");
        verify(assignmentRepository, never()).save(any());
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private Member member(Promoter promoter) {
        Person person = new Person();
        person.setId(1L);
        person.setUuid(UUID.randomUUID());
        person.setFullName("Juan Pérez");

        Member m = new Member();
        m.setId(2L);
        m.setUuid(UUID.randomUUID());
        m.setPerson(person);
        m.setPromoter(promoter);
        return m;
    }

    private Promoter promoter(String code, boolean active) {
        Promoter p = new Promoter();
        p.setId(java.util.concurrent.ThreadLocalRandom.current().nextLong(3, 1_000_000));
        p.setUuid(UUID.randomUUID());
        p.setReferralCode(code);
        p.setDisplayName(code);
        p.setActive(active);
        return p;
    }

    private User user() {
        User u = new User();
        u.setId(5L);
        u.setUuid(UUID.randomUUID());
        // The service only reads the uuid; per-test stubs wire the repo lookup.
        return u;
    }
}
