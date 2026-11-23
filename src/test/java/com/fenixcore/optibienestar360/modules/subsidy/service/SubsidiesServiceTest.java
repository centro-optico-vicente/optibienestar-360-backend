package com.fenixcore.optibienestar360.modules.subsidy.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.organization.entity.SubsidyMode;
import com.fenixcore.optibienestar360.core.exception.UnprocessableDetailsException;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyBeneficiaryRequest;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyCreateRequest;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidySettingsDto;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyUpdateRequest;
import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyAuditLog;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyAuditLogRepository;
import com.fenixcore.optibienestar360.modules.subsidy.repository.SubsidyRepository;
import org.junit.jupiter.api.BeforeEach;
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
    @Mock private DefaultSortResolver defaultSortResolver;
    @Mock private SubsidyResolver subsidyResolver;
    @Mock private PromoterRepository promoterRepository;

    private SubsidiesService sut() {
        return new SubsidiesService(repository, auditLogRepository, memberRepository,
                membershipRepository, beneficiaryRepository, userRepository, defaultSortResolver,
                subsidyResolver, promoterRepository);
    }

    @BeforeEach
    void modeEnablesBothFees() {
        lenient().when(subsidyResolver.currentMode()).thenReturn(SubsidyMode.MONTHLY_AND_INSCRIPTION);
    }

    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void create_persists_andWritesCreatedAudit() {
        Member member = member(1L);
        stubMemberAndActor(member);
        when(repository.save(any(Subsidy.class))).thenAnswer(inv -> inv.getArgument(0));

        sut().create(req(member.getUuid(), "100", null, null, null, List.of()), ACTOR, false);

        verify(repository).save(any(Subsidy.class));
        ArgumentCaptor<SubsidyAuditLog> captor = ArgumentCaptor.forClass(SubsidyAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(SubsidyAuditLog.Action.CREATED);
    }

    @Test
    void create_coverageRequired_whenBothPercentagesNull() {
        Member member = member(1L);
        stubMemberAndActor(member);

        assertThatThrownBy(() -> sut().create(req(member.getUuid(), null, null, null, null, List.of()), ACTOR, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.coverage.required");
        verify(repository, never()).save(any());
    }

    @Test
    void create_windowInvalid_whenUntilBeforeFrom() {
        Member member = member(1L);
        stubMemberAndActor(member);

        SubsidyCreateRequest req = new SubsidyCreateRequest(member.getUuid(), new BigDecimal("100"), null,
                null, "Fundación X", LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 1), List.of(), null);

        assertThatThrownBy(() -> sut().create(req, ACTOR, false))
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
        assertThatThrownBy(() -> sut().create(req(member.getUuid(), "100", null, 1, null, lines), ACTOR, false))
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

        assertThatThrownBy(() -> sut().create(req(member.getUuid(), "100", null, null, null, lines), ACTOR, false))
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

    // ─── Organization subsidy mode (hub ADR 0041) ─────────────────────────────

    @Test
    void create_rejected_whenModeIsDisabled() {
        when(subsidyResolver.currentMode()).thenReturn(SubsidyMode.DISABLED);

        assertThatThrownBy(() -> sut().create(req(UUID.randomUUID(), "50", null, null, null, List.of()), ACTOR, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.disabled_for_organization");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsInscriptionPercentage_whenModeIsMonthlyOnly() {
        when(subsidyResolver.currentMode()).thenReturn(SubsidyMode.MONTHLY);

        assertThatThrownBy(() -> sut().create(req(UUID.randomUUID(), "50", "100", null, null, List.of()), ACTOR, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.fee_type_disabled");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsBeneficiaryMonthlyLine_whenModeIsInscriptionOnly() {
        when(subsidyResolver.currentMode()).thenReturn(SubsidyMode.INSCRIPTION);
        List<SubsidyBeneficiaryRequest> lines =
                List.of(new SubsidyBeneficiaryRequest(UUID.randomUUID(), new BigDecimal("50"), null));

        assertThatThrownBy(() -> sut().create(req(UUID.randomUUID(), null, "100", null, null, lines), ACTOR, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.fee_type_disabled");
    }

    @Test
    void create_marksTheSubsidyManual() {
        Member member = member(1L);
        stubMemberAndActor(member);
        when(repository.save(any(Subsidy.class))).thenAnswer(inv -> inv.getArgument(0));

        sut().create(req(member.getUuid(), "50", null, null, null, List.of()), ACTOR, false);

        ArgumentCaptor<Subsidy> captor = ArgumentCaptor.forClass(Subsidy.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(Subsidy.Origin.MANUAL);
    }

    @Test
    void update_rejected_whenModeIsDisabled() {
        Subsidy subsidy = new Subsidy();
        subsidy.setUuid(UUID.randomUUID());
        subsidy.setMember(member(1L));
        when(repository.findByUuid(subsidy.getUuid())).thenReturn(Optional.of(subsidy));
        when(subsidyResolver.currentMode()).thenReturn(SubsidyMode.DISABLED);

        assertThatThrownBy(() -> sut().update(subsidy.getUuid(), updateReq("60"), ACTOR, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("subsidy.disabled_for_organization");
    }

    @Test
    void update_ofReferralReward_isNotGovernedByTheMode() {
        Subsidy reward = new Subsidy();
        reward.setUuid(UUID.randomUUID());
        reward.setMember(member(1L));
        reward.setOrigin(Subsidy.Origin.REFERRAL_REWARD);
        reward.setMonthlyPercentage(new BigDecimal("100"));
        reward.setMaxExoneratedBeneficiaries(0);
        reward.setValidFrom(LocalDate.of(2026, 1, 1));
        when(repository.findByUuid(reward.getUuid())).thenReturn(Optional.of(reward));
        when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));
        lenient().when(subsidyResolver.currentMode()).thenReturn(SubsidyMode.DISABLED);

        sut().update(reward.getUuid(), updateReq("50"), ACTOR, false);

        assertThat(reward.getMonthlyPercentage()).isEqualByComparingTo("50");
    }

    @Test
    void settings_reflectTheOrganizationMode() {
        when(subsidyResolver.currentMode()).thenReturn(SubsidyMode.INSCRIPTION);

        SubsidySettingsDto settings = sut().settings(ACTOR, false);

        assertThat(settings.mode()).isEqualTo("INSCRIPTION");
        assertThat(settings.monthlyEnabled()).isFalse();
        assertThat(settings.inscriptionEnabled()).isTrue();
    }

    // ─── Discount cap (hub ADR 0041 §5) ───────────────────────────────────────

    @Test
    void create_advisorAboveCap_withoutPermission_isRejectedWithTheCap() {
        advisorWithCap("30");

        assertThatThrownBy(() -> sut().create(req(UUID.randomUUID(), "50", null, null, null, List.of()), ACTOR, false))
                .isInstanceOf(UnprocessableDetailsException.class)
                .hasMessage("subsidy.exceeds_discount_cap")
                .satisfies(ex -> {
                    UnprocessableDetailsException u = (UnprocessableDetailsException) ex;
                    assertThat(u.getArgs()).containsExactly("30");
                    assertThat(u.getProperties()).containsEntry("code", "subsidy.exceeds_discount_cap");
                    assertThat((BigDecimal) u.getProperties().get("discountCapPct")).isEqualByComparingTo("30");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void create_advisorAboveCap_onABeneficiaryLine_isRejected() {
        advisorWithCap("30");
        List<SubsidyBeneficiaryRequest> lines =
                List.of(new SubsidyBeneficiaryRequest(UUID.randomUUID(), null, new BigDecimal("100")));

        assertThatThrownBy(() -> sut().create(req(UUID.randomUUID(), "10", null, null, null, lines), ACTOR, false))
                .hasMessage("subsidy.exceeds_discount_cap");
    }

    @Test
    void create_advisorAboveCap_withPermission_needsAcknowledgement() {
        advisorWithCap("30");

        assertThatThrownBy(() -> sut().create(req(UUID.randomUUID(), "50", null, null, null, List.of()), ACTOR, true))
                .isInstanceOf(UnprocessableDetailsException.class)
                .hasMessage("subsidy.exceeds_discount_cap_ack_required");
        verify(repository, never()).save(any());
    }

    @Test
    void create_advisorAboveCap_withPermissionAndAcknowledgement_isAudited() {
        advisorWithCap("30");
        Member member = member(1L);
        stubMemberAndActor(member);
        when(repository.save(any(Subsidy.class))).thenAnswer(inv -> inv.getArgument(0));
        SubsidyCreateRequest acknowledged = new SubsidyCreateRequest(member.getUuid(), new BigDecimal("50"), null,
                null, "Fundación X", LocalDate.of(2026, 1, 1), null, List.of(), true);

        sut().create(acknowledged, ACTOR, true);

        ArgumentCaptor<SubsidyAuditLog> captor = ArgumentCaptor.forClass(SubsidyAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAfter()).containsEntry("discountCapExceeded", true);
        assertThat((BigDecimal) captor.getValue().getAfter().get("discountCapPct")).isEqualByComparingTo("30");
    }

    @Test
    void create_advisorWithinCap_isAccepted() {
        advisorWithCap("50");
        Member member = member(1L);
        stubMemberAndActor(member);
        when(repository.save(any(Subsidy.class))).thenAnswer(inv -> inv.getArgument(0));

        sut().create(req(member.getUuid(), "50", null, null, null, List.of()), ACTOR, false);

        ArgumentCaptor<SubsidyAuditLog> captor = ArgumentCaptor.forClass(SubsidyAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getAfter()).doesNotContainKey("discountCapExceeded");
    }

    @Test
    void settings_exposeTheCallersCap() {
        advisorWithCap("25");

        SubsidySettingsDto settings = sut().settings(ACTOR, true);

        assertThat(settings.discountCapPct()).isEqualByComparingTo("25");
        assertThat(settings.canExceedCap()).isTrue();
    }

    @Test
    void settings_backOfficeUser_isUncapped() {
        when(promoterRepository.findActiveByUserUuid(ACTOR)).thenReturn(Optional.empty());

        assertThat(sut().settings(ACTOR, false).discountCapPct()).isNull();
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void advisorWithCap(String pct) {
        PromoterRank rank = new PromoterRank();
        rank.setMaxDiscountPct(new BigDecimal(pct));
        Promoter promoter = new Promoter();
        promoter.setRank(rank);
        when(promoterRepository.findActiveByUserUuid(ACTOR)).thenReturn(Optional.of(promoter));
    }

    private static SubsidyUpdateRequest updateReq(String monthly) {
        return new SubsidyUpdateRequest(new BigDecimal(monthly), null, null, null, null, null, null, null, null);
    }

    private void stubMemberAndActor(Member member) {
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        lenient().when(userRepository.findByUuid(ACTOR)).thenReturn(Optional.of(user()));
    }

    private static SubsidyCreateRequest req(UUID memberUuid, String monthly, String inscription,
                                            Integer cap, LocalDate until, List<SubsidyBeneficiaryRequest> lines) {
        return new SubsidyCreateRequest(memberUuid,
                monthly != null ? new BigDecimal(monthly) : null,
                inscription != null ? new BigDecimal(inscription) : null,
                cap, "Fundación X", LocalDate.of(2026, 1, 1), until, lines, null);
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
