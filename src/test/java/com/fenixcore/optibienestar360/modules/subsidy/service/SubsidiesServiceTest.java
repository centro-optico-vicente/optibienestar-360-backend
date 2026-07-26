package com.fenixcore.optibienestar360.modules.subsidy.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyBeneficiaryRequest;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyCreateRequest;
import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyAuditLog;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyAuditLogRepository;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubsidiesService} — CRUD with the mandatory audit
 * trail plus the cross-row validations the DB CHECKs can't express (coverage,
 * validity window, beneficiary ownership, exoneration cap).
 */
@ExtendWith(MockitoExtension.class)
class SubsidiesServiceTest {

    @Mock private SubsidyRepository repository;
    @Mock private SubsidyAuditLogRepository auditLogRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private UserRepository userRepository;

    private SubsidiesService sut() {
        return new SubsidiesService(repository, auditLogRepository, memberRepository,
                membershipRepository, beneficiaryRepository, userRepository);
    }

    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void create_persists_andWritesCreatedAudit() {
        Member member = member(1L);
        stubMemberAndActor(member);
        when(repository.save(any(Subsidy.class))).thenAnswer(inv -> inv.getArgument(0));

        sut().create(req(member.getUuid(), "100", null, null, null, List.of()), ACTOR);

        verify(repository).save(any(Subsidy.class));
        ArgumentCaptor<SubsidyAuditLog> captor = ArgumentCaptor.forClass(SubsidyAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(SubsidyAuditLog.Action.CREATED);
    }

    @Test
    void create_coverageRequired_whenBothPercentagesNull() {
        Member member = member(1L);
        stubMemberAndActor(member);

        assertThatThrownBy(() -> sut().create(req(member.getUuid(), null, null, null, null, List.of()), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.coverage.required");
        verify(repository, never()).save(any());
    }

    @Test
    void create_windowInvalid_whenUntilBeforeFrom() {
        Member member = member(1L);
        stubMemberAndActor(member);

        SubsidyCreateRequest req = new SubsidyCreateRequest(member.getUuid(), new BigDecimal("100"), null,
                null, "Fundación X", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1), List.of());

        assertThatThrownBy(() -> sut().create(req, ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.valid_until.before_from");
        verify(repository, never()).save(any());
    }

    @Test
    void create_capExceeded_whenMoreLinesThanCap() {
        Member member = member(1L);
        stubMemberAndActor(member);
        List<SubsidyBeneficiaryRequest> lines = List.of(
                new SubsidyBeneficiaryRequest(UUID.randomUUID(), null, new BigDecimal("100")),
                new SubsidyBeneficiaryRequest(UUID.randomUUID(), null, new BigDecimal("100")));

        // cap = 1 but two beneficiary lines supplied.
        assertThatThrownBy(() -> sut().create(req(member.getUuid(), "100", null, 1, null, lines), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.beneficiaries.cap_exceeded");
        verify(repository, never()).save(any());
    }

    @Test
    void create_beneficiaryNotInMember_isRejected() {
        Member member = member(1L);
        stubMemberAndActor(member);
        // No explicit cap → falls back to the plan cap; no active membership → unbounded.
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(1L)).thenReturn(Optional.empty());

        Beneficiary otherMembersBeneficiary = new Beneficiary();
        otherMembersBeneficiary.setMember(member(999L));   // belongs to a different member
        UUID benUuid = UUID.randomUUID();
        when(beneficiaryRepository.findByUuid(benUuid)).thenReturn(Optional.of(otherMembersBeneficiary));

        List<SubsidyBeneficiaryRequest> lines =
                List.of(new SubsidyBeneficiaryRequest(benUuid, null, new BigDecimal("100")));

        assertThatThrownBy(() -> sut().create(req(member.getUuid(), "100", null, null, null, lines), ACTOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.beneficiary.not_in_member");
        verify(repository, never()).save(any());
    }

    @Test
    void revoke_marksInactive_andWritesRevokedAudit() {
        Subsidy subsidy = new Subsidy();
        subsidy.setUuid(UUID.randomUUID());
        subsidy.setMember(member(1L));
        subsidy.setActive(true);
        when(repository.findByUuid(subsidy.getUuid())).thenReturn(Optional.of(subsidy));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));

        sut().revoke(subsidy.getUuid(), ACTOR);

        assertThat(subsidy.isActive()).isFalse();
        ArgumentCaptor<SubsidyAuditLog> captor = ArgumentCaptor.forClass(SubsidyAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(SubsidyAuditLog.Action.REVOKED);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void stubMemberAndActor(Member member) {
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        lenient().when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));
    }

    private static SubsidyCreateRequest req(UUID memberUuid, String monthly, String inscription,
                                            Integer cap, LocalDate until, List<SubsidyBeneficiaryRequest> lines) {
        return new SubsidyCreateRequest(memberUuid,
                monthly != null ? new BigDecimal(monthly) : null,
                inscription != null ? new BigDecimal(inscription) : null,
                cap, "Fundación X", LocalDate.of(2026, 1, 1), until, lines);
    }

    private static Member member(long id) {
        Member m = new Member();
        m.setId(id);
        m.setUuid(UUID.randomUUID());
        return m;
    }

    private static User user() {
        User u = new User();
        u.setId(1L);
        u.setUuid(UUID.randomUUID());
        return u;
    }
}
