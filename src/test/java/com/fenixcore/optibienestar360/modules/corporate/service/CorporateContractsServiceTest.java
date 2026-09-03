package com.fenixcore.optibienestar360.modules.corporate.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateBulkEnrollResponse;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateBulkEnrollResponse.Outcome;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractCreateRequest;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractDto;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractUpdateRequest;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateMemberBulkRequest;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract.PayerMode;
import com.fenixcore.optibienestar360.modules.corporate.repository.CorporateContractRepository;
import com.fenixcore.optibienestar360.modules.member.dto.MemberCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.MemberListItemDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
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
 * Unit tests for {@link CorporateContractsService} (v2 PDF — "Contratos
 * Corporativos", V38): the CORPORATIVO-plan guard, contact-user resolution,
 * partial update, soft-delete, and the bulk-enroll orchestration (skip
 * duplicates + re-sync the member count).
 */
@ExtendWith(MockitoExtension.class)
class CorporateContractsServiceTest {

    @Mock private CorporateContractRepository repository;
    @Mock private PlanRepository planRepository;
    @Mock private UserRepository userRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MembersService membersService;
    @Mock private MemberMapper memberMapper;
    @Mock private DefaultSortResolver defaultSortResolver;

    private CorporateContractsService sut() {
        lenient().when(defaultSortResolver.withDefaultSortIfUnsorted(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));
        return new CorporateContractsService(repository, planRepository, userRepository,
                memberRepository, membersService, memberMapper, defaultSortResolver);
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void create_persistsContract_forCorporatePlan() {
        Plan plan = plan(PlanType.CORPORATIVO);
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(repository.save(any())).thenAnswer(inv -> {
            CorporateContract c = inv.getArgument(0);
            c.setUuid(UUID.randomUUID());
            return c;
        });

        CorporateContractDto dto = sut().create(new CorporateContractCreateRequest(
                plan.getUuid(), "Clínica X", "J-123456789", null, PayerMode.INSTITUTION_BULK, 100));

        CorporateContract saved = capture();
        assertThat(saved.getPlan()).isSameAs(plan);
        assertThat(saved.getInstitutionName()).isEqualTo("Clínica X");
        assertThat(saved.getInstitutionTaxId()).isEqualTo("J-123456789");
        assertThat(saved.getPayerMode()).isEqualTo(PayerMode.INSTITUTION_BULK);
        assertThat(saved.getContactUser()).isNull();
        assertThat(saved.getActualMemberCount()).isZero();
        assertThat(dto.plan().name()).isEqualTo(plan.getName());
        assertThat(dto.expectedMemberCount()).isEqualTo(100);
    }

    @Test
    void create_resolvesContactUser_whenProvided() {
        Plan plan = plan(PlanType.CORPORATIVO);
        User contact = user();
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(userRepository.findByUuid(contact.getUuid())).thenReturn(Optional.of(contact));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut().create(new CorporateContractCreateRequest(
                plan.getUuid(), "Clínica X", "J-1", contact.getUuid(), PayerMode.INSTITUTION_BULK, null));

        assertThat(capture().getContactUser()).isSameAs(contact);
    }

    @Test
    void create_rejectsNonCorporatePlan() {
        Plan plan = plan(PlanType.INDIVIDUAL);
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> sut().create(new CorporateContractCreateRequest(
                plan.getUuid(), "X", "J-1", null, PayerMode.INSTITUTION_BULK, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("corporate_contract.plan.not_corporate");
        verify(repository, never()).save(any());
    }

    @Test
    void create_404_whenPlanUnknown() {
        UUID planUuid = UUID.randomUUID();
        when(planRepository.findByUuid(planUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().create(new CorporateContractCreateRequest(
                planUuid, "X", "J-1", null, PayerMode.INSTITUTION_BULK, null)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("plan.not_found");
    }

    @Test
    void create_404_whenContactUserUnknown() {
        Plan plan = plan(PlanType.CORPORATIVO);
        UUID contactUuid = UUID.randomUUID();
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(userRepository.findByUuid(contactUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().create(new CorporateContractCreateRequest(
                plan.getUuid(), "X", "J-1", contactUuid, PayerMode.INSTITUTION_BULK, null)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("user.not_found");
        verify(repository, never()).save(any());
    }

    // ─── update / delete ────────────────────────────────────────────────────

    @Test
    void update_appliesPartialChanges() {
        CorporateContract contract = contractEntity(PayerMode.INSTITUTION_BULK);
        when(repository.findByUuid(contract.getUuid())).thenReturn(Optional.of(contract));

        sut().update(contract.getUuid(), new CorporateContractUpdateRequest(
                null, "Renamed", null, null, PayerMode.INDIVIDUAL_PAYER, 250, false, "ARCHIVED"));

        assertThat(contract.getInstitutionName()).isEqualTo("Renamed");
        assertThat(contract.getPayerMode()).isEqualTo(PayerMode.INDIVIDUAL_PAYER);
        assertThat(contract.getExpectedMemberCount()).isEqualTo(250);
        assertThat(contract.isActive()).isFalse();
        assertThat(contract.getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void update_rejectsNonCorporatePlan_whenPlanChanged() {
        CorporateContract contract = contractEntity(PayerMode.INSTITUTION_BULK);
        Plan individual = plan(PlanType.INDIVIDUAL);
        when(repository.findByUuid(contract.getUuid())).thenReturn(Optional.of(contract));
        when(planRepository.findByUuid(individual.getUuid())).thenReturn(Optional.of(individual));

        assertThatThrownBy(() -> sut().update(contract.getUuid(), new CorporateContractUpdateRequest(
                individual.getUuid(), null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("corporate_contract.plan.not_corporate");
    }

    @Test
    void update_404_whenContractUnknown() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().update(uuid, new CorporateContractUpdateRequest(
                null, "New", null, null, null, null, null, null)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("corporate_contract.not_found");
    }

    @Test
    void delete_softDeletes() {
        CorporateContract contract = contractEntity(PayerMode.INSTITUTION_BULK);
        when(repository.findByUuid(contract.getUuid())).thenReturn(Optional.of(contract));

        sut().delete(contract.getUuid());

        assertThat(contract.isActive()).isFalse();
    }

    // ─── bulk enroll ──────────────────────────────────────────────────────────

    @Test
    void enrollMembers_enrollsSkipsDuplicates_andResyncsCount() {
        CorporateContract contract = contractEntity(PayerMode.INSTITUTION_BULK);
        when(repository.findByUuid(contract.getUuid())).thenReturn(Optional.of(contract));
        Member enrolled = new Member();
        enrolled.setUuid(UUID.randomUUID());
        // First row enrolls, second is a duplicate (null) → skipped.
        when(membersService.enrollForCorporate(any(), any())).thenReturn(enrolled, (Member) null);
        when(memberRepository.countByCorporateContractIdAndActiveTrue(contract.getId())).thenReturn(1L);

        CorporateBulkEnrollResponse resp = sut().enrollMembers(contract.getUuid(),
                new CorporateMemberBulkRequest(List.of(
                        memberReq("12345678", "Juan", "Pérez"),
                        memberReq("87654321", "Ana", "Gómez"))));

        assertThat(resp.requested()).isEqualTo(2);
        assertThat(resp.enrolled()).isEqualTo(1);
        assertThat(resp.skipped()).isEqualTo(1);
        assertThat(resp.results()).hasSize(2);
        assertThat(resp.results().get(0).outcome()).isEqualTo(Outcome.ENROLLED);
        assertThat(resp.results().get(0).documentNumber()).isEqualTo("12345678");
        assertThat(resp.results().get(0).memberUuid()).isEqualTo(enrolled.getUuid());
        assertThat(resp.results().get(1).outcome()).isEqualTo(Outcome.SKIPPED_DUPLICATE);
        assertThat(resp.results().get(1).memberUuid()).isNull();
        assertThat(contract.getActualMemberCount()).isEqualTo(1);
    }

    @Test
    void enrollMembers_404_whenContractUnknown() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().enrollMembers(uuid, new CorporateMemberBulkRequest(
                List.of(memberReq("1", "A", "B")))))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("corporate_contract.not_found");
        verify(membersService, never()).enrollForCorporate(any(), any());
    }

    // ─── read ─────────────────────────────────────────────────────────────────

    @Test
    void listMembers_returnsMappedPage() {
        CorporateContract contract = contractEntity(PayerMode.INSTITUTION_BULK);
        contract.setId(7L);
        when(repository.findByUuid(contract.getUuid())).thenReturn(Optional.of(contract));
        Member member = new Member();
        MemberListItemDto dto = new MemberListItemDto(UUID.randomUUID(), "Juan Pérez", "V",
                "12345678", null, null, LocalDate.of(2026, 1, 1), null, true, null, null, null);
        when(memberRepository.findByCorporateContractIdAndActiveTrue(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(member)));
        when(memberMapper.toListItem(member)).thenReturn(dto);

        assertThat(sut().listMembers(contract.getUuid(), PageRequest.of(0, 20)).getContent())
                .containsExactly(dto);
    }

    @Test
    void get_returnsDto() {
        CorporateContract contract = contractEntity(PayerMode.INSTITUTION_BULK);
        when(repository.findByUuid(contract.getUuid())).thenReturn(Optional.of(contract));

        CorporateContractDto dto = sut().get(contract.getUuid());

        assertThat(dto.uuid()).isEqualTo(contract.getUuid());
        assertThat(dto.payerMode()).isEqualTo(PayerMode.INSTITUTION_BULK);
    }

    @Test
    void get_404_whenUnknown() {
        UUID uuid = UUID.randomUUID();
        when(repository.findByUuid(uuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().get(uuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("corporate_contract.not_found");
    }

    @Test
    void list_rejectsDisallowedFilterField() {
        assertThatThrownBy(() -> sut().list(PageRequest.of(0, 20), "secret==x", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("corporate_contract.filter.field_not_allowed");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CorporateContract capture() {
        ArgumentCaptor<CorporateContract> captor = ArgumentCaptor.forClass(CorporateContract.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private CorporateContract contractEntity(PayerMode mode) {
        CorporateContract c = new CorporateContract();
        c.setId(1L);
        c.setUuid(UUID.randomUUID());
        c.setPlan(plan(PlanType.CORPORATIVO));
        c.setInstitutionName("Original");
        c.setInstitutionTaxId("J-000");
        c.setPayerMode(mode);
        c.setActive(true);
        return c;
    }

    private Plan plan(PlanType type) {
        Plan p = new Plan();
        p.setId(1L);
        p.setUuid(UUID.randomUUID());
        p.setName("Corp Plan");
        p.setType(type);
        return p;
    }

    private User user() {
        User u = new User();
        u.setId(1L);
        u.setUuid(UUID.randomUUID());
        return u;
    }

    /** Minimal member payload — only the fields the bulk flow echoes are populated. */
    private MemberCreateRequest memberReq(String docNum, String first, String last) {
        return new MemberCreateRequest(
                first, null, last, null,
                "V", docNum, null, null,
                LocalDate.of(1990, 1, 1),
                null, null,
                null, null, null,
                null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null);
    }
}
