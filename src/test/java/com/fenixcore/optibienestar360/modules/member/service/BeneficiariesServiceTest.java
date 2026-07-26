package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryCreateRequest;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary.Relationship;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.payment.service.BeneficiaryInscriptionBiller;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BeneficiariesService} — the v2 beneficiary billing
 * rules from the flyer: an active membership is required, the plan's
 * {@code max_beneficiaries} is a hard cap, and beneficiaries beyond
 * {@code included_beneficiaries} trigger a one-time extra inscription charge.
 */
@ExtendWith(MockitoExtension.class)
class BeneficiariesServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private PersonService personService;
    @Mock private BeneficiaryInscriptionBiller inscriptionBiller;
    @Mock private MemberMapper mapper;

    private BeneficiariesService sut() {
        return new BeneficiariesService(memberRepository, beneficiaryRepository, membershipRepository,
                personService, inscriptionBiller, mapper);
    }

    private static final BigDecimal FEE = new BigDecimal("5.00");

    @Test
    void add_noActiveMembership_is422() {
        Member member = member();
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().add(member.getUuid(), req(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member.beneficiary.no_active_membership");
        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    void add_withinIncludedCap_noCharge() {
        Member member = member();
        stubEnrollment(member, plan(3, 5, FEE), 0L);   // Familiar: 3 included, 0 used

        sut().add(member.getUuid(), req(null));

        Beneficiary saved = captureSaved();
        assertThat(saved.isExtraInscriptionPaid()).isFalse();
        assertThat(saved.getInscriptionPaymentId()).isNull();
        verify(inscriptionBiller, never()).chargeExtraInscription(any(), any());
    }

    @Test
    void add_beyondIncludedCap_generatesCharge() {
        Member member = member();
        Membership ms = stubEnrollment(member, plan(3, 5, FEE), 3L);   // 3 included already used
        when(inscriptionBiller.chargeExtraInscription(ms, FEE)).thenReturn(77L);

        sut().add(member.getUuid(), req(null));

        Beneficiary saved = captureSaved();
        assertThat(saved.getInscriptionPaymentId()).isEqualTo(77L);
        assertThat(saved.isExtraInscriptionPaid()).isFalse();   // PENDING until approved
        verify(inscriptionBiller).chargeExtraInscription(ms, FEE);
    }

    @Test
    void add_capExceeded_is422() {
        Member member = member();
        stubEnrollment(member, plan(3, 5, FEE), 5L);   // at the hard cap

        assertThatThrownBy(() -> sut().add(member.getUuid(), req(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member.beneficiary.cap_exceeded");
        verify(beneficiaryRepository, never()).save(any());
        verify(inscriptionBiller, never()).chargeExtraInscription(any(), any());
    }

    @Test
    void add_noCap_whenMaxNull() {
        Member member = member();
        Membership ms = stubEnrollment(member, plan(0, null, FEE), 50L);   // Corporativo: no hard cap
        when(inscriptionBiller.chargeExtraInscription(ms, FEE)).thenReturn(9L);

        sut().add(member.getUuid(), req(null));   // no throw despite 50 existing

        verify(beneficiaryRepository).save(any());
        verify(inscriptionBiller).chargeExtraInscription(ms, FEE);   // included=0 → always beyond
    }

    @Test
    void add_reactivatesExisting_withoutRecharge() {
        Member member = member();
        Person person = person();
        Beneficiary existing = new Beneficiary();
        existing.setUuid(UUID.randomUUID());
        existing.setActive(false);              // soft-deleted
        existing.setInscriptionPaymentId(42L);  // inscription already settled in the past
        existing.setExtraInscriptionPaid(true);
        Membership ms = stubEnrollment(member, plan(0, 5, FEE), 0L, person, Optional.of(existing));

        sut().add(member.getUuid(), req(null));

        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getInscriptionPaymentId()).isEqualTo(42L);   // untouched
        verify(inscriptionBiller, never()).chargeExtraInscription(any(), any());
    }

    @Test
    void add_respectsExplicitExtraPaid_noCharge() {
        Member member = member();
        stubEnrollment(member, plan(0, 5, FEE), 0L);   // beyond included, but admin marks it paid

        sut().add(member.getUuid(), req(true));

        Beneficiary saved = captureSaved();
        assertThat(saved.isExtraInscriptionPaid()).isTrue();
        assertThat(saved.getInscriptionPaymentId()).isNull();
        verify(inscriptionBiller, never()).chargeExtraInscription(any(), any());
    }

    @Test
    void delete_softDeletes_withoutRefund() {
        Member member = member();
        Beneficiary b = new Beneficiary();
        b.setUuid(UUID.randomUUID());
        b.setMember(member);
        b.setActive(true);
        when(beneficiaryRepository.findByUuid(b.getUuid())).thenReturn(Optional.of(b));

        sut().delete(member.getUuid(), b.getUuid());

        assertThat(b.isActive()).isFalse();
        verify(inscriptionBiller, never()).chargeExtraInscription(any(), any());
    }

    // ─── self-service (/v1/me/family) ─────────────────────────────────────────

    @Test
    void listForUser_returnsOwnFamily() {
        Member member = member();
        UUID userUuid = UUID.randomUUID();
        when(memberRepository.findByUserUuid(userUuid)).thenReturn(Optional.of(member));
        when(beneficiaryRepository.findByMemberIdAndActiveTrue(member.getId()))
                .thenReturn(List.of(new Beneficiary()));

        assertThat(sut().listForUser(userUuid)).hasSize(1);
    }

    @Test
    void listForUser_404_whenNotEnrolled() {
        UUID userUuid = UUID.randomUUID();
        when(memberRepository.findByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().listForUser(userUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.member.not_enrolled");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    /** Wires member + active membership + a fresh person + no existing beneficiary row. */
    private Membership stubEnrollment(Member member, Plan plan, long activeCount) {
        return stubEnrollment(member, plan, activeCount, person(), Optional.empty());
    }

    private Membership stubEnrollment(Member member, Plan plan, long activeCount,
                                      Person person, Optional<Beneficiary> existing) {
        Membership ms = membership(member, plan);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId())).thenReturn(Optional.of(ms));
        when(personService.findOrCreate(any())).thenReturn(person);
        when(beneficiaryRepository.findByMemberIdAndPersonId(anyLong(), anyLong())).thenReturn(existing);
        when(beneficiaryRepository.countByMemberIdAndActiveTrue(member.getId())).thenReturn(activeCount);
        // lenient: the cap-exceeded path throws before reaching save().
        lenient().when(beneficiaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return ms;
    }

    private Beneficiary captureSaved() {
        ArgumentCaptor<Beneficiary> captor = ArgumentCaptor.forClass(Beneficiary.class);
        verify(beneficiaryRepository).save(captor.capture());
        return captor.getValue();
    }

    private Member member() {
        Member m = new Member();
        m.setId(1L);
        m.setUuid(UUID.randomUUID());
        return m;
    }

    private Plan plan(int included, Integer max, BigDecimal extraFee) {
        Plan p = new Plan();
        p.setId(1L);
        p.setUuid(UUID.randomUUID());
        p.setIncludedBeneficiaries(included);
        p.setMaxBeneficiaries(max);
        p.setExtraBeneficiaryInscriptionFee(extraFee);
        return p;
    }

    private Membership membership(Member member, Plan plan) {
        Membership ms = new Membership();
        ms.setId(1L);
        ms.setMember(member);
        ms.setPlan(plan);
        ms.setActive(true);
        return ms;
    }

    private Person person() {
        Person p = new Person();
        p.setId(2L);
        p.setUuid(UUID.randomUUID());
        return p;
    }

    private BeneficiaryCreateRequest req(Boolean extraPaid) {
        return new BeneficiaryCreateRequest("Ana", null, "Pérez", null, "V", "12345678",
                LocalDate.of(2015, 5, 20), null, null, Relationship.CHILD, extraPaid);
    }
}
