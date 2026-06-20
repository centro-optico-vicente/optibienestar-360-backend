package com.fenixcore.optisaludplus.modules.promoter.service;

import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.member.repository.MemberRepository;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.promoter.dto.ReferralCodeIssueRequest;
import com.fenixcore.optisaludplus.modules.promoter.dto.ReferralCodeIssueResponse;
import com.fenixcore.optisaludplus.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the admin referral-code issuance service. Mocks both
 * repos; verifies the custom-code uniqueness guards, the auto-generate
 * collision retry, the idempotent re-issue with the same custom code,
 * and the active-member precondition.
 */
@ExtendWith(MockitoExtension.class)
class ReferralCodeServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private PromoterRepository promoterRepository;

    @InjectMocks private ReferralCodeService service;

    // ─── Resolution + preconditions ─────────────────────────────────────────

    @Test
    void issue_404_when_member_uuid_unknown() {
        UUID unknown = UUID.randomUUID();
        when(memberRepository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(new ReferralCodeIssueRequest(unknown, null)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("member.not_found");
    }

    @Test
    void issue_422_when_member_inactive() {
        Member m = member(10L, null);
        m.setActive(false);
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.issue(new ReferralCodeIssueRequest(m.getUuid(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("referral_code.member.inactive");
    }

    // ─── Custom code path ──────────────────────────────────────────────────

    @Test
    void issue_assigns_custom_code_and_returns_previous() {
        Member m = member(10L, "OLD123");
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
        when(promoterRepository.existsByReferralCode("NEW456")).thenReturn(false);
        when(memberRepository.findByReferralCode("NEW456")).thenReturn(Optional.empty());

        ReferralCodeIssueResponse resp = service.issue(
                new ReferralCodeIssueRequest(m.getUuid(), "NEW456"));

        assertThat(resp.referralCode()).isEqualTo("NEW456");
        assertThat(resp.previousCode()).isEqualTo("OLD123");
        assertThat(resp.generated()).isFalse();
        assertThat(resp.memberFullName()).isEqualTo("John Doe");
        assertThat(m.getReferralCode()).isEqualTo("NEW456");
    }

    @Test
    void issue_trims_custom_code_whitespace() {
        Member m = member(10L, null);
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
        when(promoterRepository.existsByReferralCode("ABC123")).thenReturn(false);
        when(memberRepository.findByReferralCode("ABC123")).thenReturn(Optional.empty());

        ReferralCodeIssueResponse resp = service.issue(
                new ReferralCodeIssueRequest(m.getUuid(), "  ABC123  "));

        assertThat(resp.referralCode()).isEqualTo("ABC123");
    }

    @Test
    void issue_rejects_custom_code_collision_with_promoter() {
        Member m = member(10L, null);
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
        when(promoterRepository.existsByReferralCode("TAKEN1")).thenReturn(true);

        assertThatThrownBy(() -> service.issue(
                new ReferralCodeIssueRequest(m.getUuid(), "TAKEN1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("referral_code.duplicate.promoter");
    }

    @Test
    void issue_rejects_custom_code_collision_with_other_member() {
        Member target = member(10L, null);
        Member other = member(11L, "TAKEN2");
        when(memberRepository.findByUuid(target.getUuid())).thenReturn(Optional.of(target));
        when(promoterRepository.existsByReferralCode("TAKEN2")).thenReturn(false);
        when(memberRepository.findByReferralCode("TAKEN2")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.issue(
                new ReferralCodeIssueRequest(target.getUuid(), "TAKEN2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("referral_code.duplicate.member");
    }

    @Test
    void issue_is_idempotent_when_custom_code_equals_current() {
        Member m = member(10L, "SAME99");
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));

        ReferralCodeIssueResponse resp = service.issue(
                new ReferralCodeIssueRequest(m.getUuid(), "SAME99"));

        assertThat(resp.referralCode()).isEqualTo("SAME99");
        assertThat(resp.previousCode()).isEqualTo("SAME99");
        assertThat(resp.generated()).isFalse();
        // Short-circuit: no uniqueness checks fired
        verify(promoterRepository, never()).existsByReferralCode(anyString());
    }

    // ─── Auto-generate path ────────────────────────────────────────────────

    @Test
    void issue_generates_code_when_custom_blank() {
        Member m = member(10L, null);
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
        when(promoterRepository.existsByReferralCode(anyString())).thenReturn(false);
        when(memberRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());

        ReferralCodeIssueResponse resp = service.issue(
                new ReferralCodeIssueRequest(m.getUuid(), null));

        assertThat(resp.generated()).isTrue();
        assertThat(resp.previousCode()).isNull();
        assertThat(resp.referralCode())
                .hasSize(ReferralCodeService.GENERATED_LENGTH)
                .matches("[" + ReferralCodeService.ALPHABET + "]+");
        assertThat(m.getReferralCode()).isEqualTo(resp.referralCode());
    }

    @Test
    void issue_generates_code_when_custom_is_only_whitespace() {
        Member m = member(10L, null);
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
        when(promoterRepository.existsByReferralCode(anyString())).thenReturn(false);
        when(memberRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());

        ReferralCodeIssueResponse resp = service.issue(
                new ReferralCodeIssueRequest(m.getUuid(), "   "));

        assertThat(resp.generated()).isTrue();
    }

    @Test
    void issue_retries_on_collision_then_succeeds() {
        Member m = member(10L, null);
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
        // First two candidates collide; subsequent are free.
        when(promoterRepository.existsByReferralCode(anyString()))
                .thenReturn(true).thenReturn(true).thenReturn(false);
        when(memberRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());

        ReferralCodeIssueResponse resp = service.issue(
                new ReferralCodeIssueRequest(m.getUuid(), null));

        assertThat(resp.generated()).isTrue();
        assertThat(resp.referralCode()).hasSize(ReferralCodeService.GENERATED_LENGTH);
    }

    @Test
    void issue_throws_when_generation_retries_exhausted() {
        Member m = member(10L, null);
        when(memberRepository.findByUuid(m.getUuid())).thenReturn(Optional.of(m));
        // Every candidate is taken on the promoter side.
        when(promoterRepository.existsByReferralCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.issue(
                new ReferralCodeIssueRequest(m.getUuid(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("referral_code.generation_exhausted");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static Member member(Long id, String currentCode) {
        Member m = new Member();
        m.setId(id);
        m.setUuid(UUID.randomUUID());
        m.setReferralCode(currentCode);
        m.setActive(true);
        Person p = new Person();
        p.setId(id + 100);
        // Person.fullName is GENERATED STORED in Postgres but the test
        // assertion only cares the service walks the link, so seed it.
        try {
            var f = Person.class.getDeclaredField("fullName");
            f.setAccessible(true);
            f.set(p, "John Doe");
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        m.setPerson(p);
        return m;
    }
}
