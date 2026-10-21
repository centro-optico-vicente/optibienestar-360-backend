package com.fenixcore.optibienestar360.modules.affiliation.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestUpdateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequestLog;
import com.fenixcore.optibienestar360.modules.affiliation.event.AffiliationRequestEvent;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestLogRepository;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipsService;
import com.fenixcore.optibienestar360.modules.person.repository.PersonRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.MemberContractService;
import com.fenixcore.optibienestar360.modules.terms.service.MemberTermsApplicant;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AffiliationRequestService}: the public submit (evidence, duplicates
 * against members/users and against PENDING requests), the reviewer's edit
 * log, approval (acceptance recorded only when the contract text is
 * unchanged) and rejection (reason required).
 */
@ExtendWith(MockitoExtension.class)
class AffiliationRequestServiceTest {

    private static final String CONTRACT = "Contrato de María Pérez, V-12345678";

    @Mock private AffiliationRequestRepository repository;
    @Mock private AffiliationRequestLogRepository logRepository;
    @Mock private PersonRepository personRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private PlanRepository planRepository;
    @Mock private UserService userService;
    @Mock private MembersService membersService;
    @Mock private MembershipsService membershipsService;
    @Mock private TermsVersionService termsVersionService;
    @Mock private TermsAcceptanceService termsAcceptanceService;
    @Mock private MemberContractService memberContractService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DefaultSortResolver defaultSortResolver;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AffiliationRequestService service;

    private Plan plan;
    private TermsVersion terms;

    @BeforeEach
    void setUp() {
        plan = new Plan();
        plan.setId(3L);
        plan.setUuid(UUID.randomUUID());
        plan.setName("Plan Individual");
        plan.setType(PlanType.INDIVIDUAL);
        plan.setActive(true);
        plan.setPublished(true);

        terms = new TermsVersion();
        terms.setId(9L);
        terms.setUuid(UUID.randomUUID());
        terms.setTermType(TermType.MEMBER);
        terms.setTitle("Acuerdo de Membresía");
        terms.setContentMarkdown("Contrato de {{member.fullName}}, {{member.document}}");
    }

    private AffiliationRequestCreateRequest createRequest(String referralCode) {
        return new AffiliationRequestCreateRequest("María", "Pérez", "V", "12345678",
                LocalDate.of(1985, 3, 15), " Maria@Example.com ", "0414-1234567", "s3cret-pass",
                plan.getUuid(), referralCode, terms.getUuid());
    }

    private AffiliationRequest pending() {
        AffiliationRequest r = new AffiliationRequest();
        r.setId(11L);
        r.setUuid(UUID.randomUUID());
        r.setFirstName("María");
        r.setLastName("Pérez");
        r.setDocumentType("V");
        r.setDocumentNumber("12345678");
        r.setBirthDate(LocalDate.of(1985, 3, 15));
        r.setEmail("maria@example.com");
        r.setLocale("es");
        r.setPlan(plan);
        r.setPasswordHash("$2a$10$hash");
        r.setTermsVersion(terms);
        r.setTermsRenderedContent(CONTRACT);
        r.setTermsContentSha256(MemberContractService.sha256(CONTRACT));
        r.setTermsAcceptedAt(Instant.parse("2026-10-20T14:00:00Z"));
        r.setIpAddress("200.1.2.3");
        r.setUserAgent("Mozilla/5.0");
        r.setStatus(AffiliationRequest.Status.PENDING.name());
        return r;
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    @Test
    void create_storesTheHashAndTheContractEvidence_andAnnouncesTheRequest() {
        Promoter advisor = new Promoter();
        advisor.setDisplayName("Ana Ventas");
        when(promoterRepository.findByReferralCode("ANAV42")).thenReturn(Optional.of(advisor));
        when(planRepository.findByUuid(plan.getUuid())).thenReturn(Optional.of(plan));
        when(termsVersionService.requireCurrentPublic(TermType.MEMBER, terms.getUuid())).thenReturn(terms);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("$2a$10$hash");
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        AffiliationRequestCreatedDto created = service.create(createRequest(" anav42 "),
                new ClientRequestMetadata("200.1.2.3", "Mozilla/5.0"));

        ArgumentCaptor<AffiliationRequest> saved = ArgumentCaptor.forClass(AffiliationRequest.class);
        verify(repository).save(saved.capture());
        AffiliationRequest r = saved.getValue();
        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getEmail()).isEqualTo("maria@example.com");
        assertThat(r.getPasswordHash()).isEqualTo("$2a$10$hash");
        assertThat(r.getReferralCode()).isEqualTo("ANAV42");
        assertThat(r.getPromoter()).isSameAs(advisor);
        assertThat(r.getTermsRenderedContent()).isEqualTo(CONTRACT);
        assertThat(r.getTermsContentSha256()).isEqualTo(MemberContractService.sha256(CONTRACT)).hasSize(64);
        assertThat(r.getIpAddress()).isEqualTo("200.1.2.3");
        assertThat(r.getTermsAcceptedAt()).isNotNull();

        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.email()).isEqualTo("maria@example.com");
        assertThat(created.advisorName()).isEqualTo("Ana Ventas");
        verify(logRepository).save(any(AffiliationRequestLog.class));
        ArgumentCaptor<AffiliationRequestEvent> event = ArgumentCaptor.forClass(AffiliationRequestEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AffiliationRequestEvent.Kind.RECEIVED);
        // Nothing is created until a reviewer approves it.
        verify(userService, never()).createUserWithPasswordHash(any(), anyString(), any());
        verify(membersService, never()).create(any());
    }

    @Test
    void create_rejectsAnExistingMemberDocument() {
        when(personRepository.existsByDocumentTypeAndDocumentNumber("V", "12345678")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null), ClientRequestMetadata.EMPTY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation.document.exists");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsAnExistingUserEmail() {
        when(userRepository.existsByEmail("maria@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null), ClientRequestMetadata.EMPTY))
                .hasMessage("user.email.exists");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsADocumentWithAnotherPendingRequest() {
        when(repository.existsByDocumentTypeAndDocumentNumberAndStatus("V", "12345678", "PENDING")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null), ClientRequestMetadata.EMPTY))
                .hasMessage("affiliation_request.document.pending");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsAnEmailWithAnotherPendingRequest() {
        when(repository.existsByEmailIgnoreCaseAndStatus("maria@example.com", "PENDING")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null), ClientRequestMetadata.EMPTY))
                .hasMessage("affiliation_request.email.pending");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsAnUnknownReferralCode() {
        when(promoterRepository.findByReferralCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(createRequest("nope"), ClientRequestMetadata.EMPTY))
                .hasMessage("affiliation.referral.not_found");
    }

    // ─── Update ─────────────────────────────────────────────────────────────

    @Test
    void update_logsOnlyTheChangedFields() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
        when(userRepository.findByUuid(reviewer.getUuid())).thenReturn(Optional.of(reviewer));
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn("Contrato corregido");

        service.update(r.getUuid(), new AffiliationRequestUpdateRequest("María", "Pérez", "V", "12345679",
                r.getBirthDate(), r.getEmail(), null, plan.getUuid(), "Cédula mal escrita"), reviewer.getUuid());

        assertThat(r.getDocumentNumber()).isEqualTo("12345679");
        ArgumentCaptor<AffiliationRequestLog> entry = ArgumentCaptor.forClass(AffiliationRequestLog.class);
        verify(logRepository).save(entry.capture());
        assertThat(entry.getValue().getAction()).isEqualTo(AffiliationRequestLog.Action.UPDATED);
        assertThat(entry.getValue().getComment()).isEqualTo("Cédula mal escrita");
        assertThat(entry.getValue().getActor()).isSameAs(reviewer);
        assertThat(entry.getValue().getChanges()).containsExactly(
                Map.of("field", "documentNumber", "from", "12345678", "to", "12345679"));
    }

    @Test
    void update_onlyWhilePending() {
        AffiliationRequest r = pending();
        r.setStatus("APPROVED");
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.update(r.getUuid(), new AffiliationRequestUpdateRequest("María", "Pérez",
                "V", "12345678", r.getBirthDate(), r.getEmail(), null, plan.getUuid(), null), UUID.randomUUID()))
                .hasMessage("affiliation_request.update.invalid_state");
    }

    // ─── Approve ────────────────────────────────────────────────────────────

    private User stubApprovalCreation(AffiliationRequest r, User reviewer) {
        Role memberRole = new Role();
        memberRole.setUuid(UUID.randomUUID());
        User created = new User();
        created.setUuid(UUID.randomUUID());
        Member member = new Member();
        member.setUuid(UUID.randomUUID());
        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        UserDto userDto = mock(UserDto.class);
        when(userDto.uuid()).thenReturn(created.getUuid());
        MemberDetailDto memberDto = mock(MemberDetailDto.class);
        when(memberDto.uuid()).thenReturn(member.getUuid());
        MembershipDto membershipDto = mock(MembershipDto.class);
        when(membershipDto.uuid()).thenReturn(membership.getUuid());

        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
        when(userRepository.findByUuid(reviewer.getUuid())).thenReturn(Optional.of(reviewer));
        when(roleRepository.findByName("AFILIADO")).thenReturn(Optional.of(memberRole));
        when(userService.createUserWithPasswordHash(any(AdminCreateUserRequest.class), eq("$2a$10$hash"),
                eq(reviewer.getUuid()))).thenReturn(userDto);
        when(membersService.create(any())).thenReturn(memberDto);
        when(membershipsService.enroll(eq(member.getUuid()), any(), eq(reviewer.getUuid()))).thenReturn(membershipDto);
        when(membershipRepository.findByUuid(membership.getUuid())).thenReturn(Optional.of(membership));
        when(userRepository.findByUuid(created.getUuid())).thenReturn(Optional.of(created));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        return created;
    }

    @Test
    void approve_withUnchangedData_recordsTheApplicantsAcceptance() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        User created = stubApprovalCreation(r, reviewer);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        service.approve(r.getUuid(), null, reviewer.getUuid());

        ArgumentCaptor<ClientRequestMetadata> evidence = ArgumentCaptor.forClass(ClientRequestMetadata.class);
        verify(termsAcceptanceService).recordSelfServiceAcceptance(eq(created), eq(terms), eq(r.getMembership()),
                evidence.capture(), eq(Instant.parse("2026-10-20T14:00:00Z")), eq(r.getTermsRenderedContent()));
        assertThat(evidence.getValue().ipAddress()).isEqualTo("200.1.2.3");
        assertThat(r.getStatus()).isEqualTo("APPROVED");
        assertThat(r.getUser()).isSameAs(created);
        assertThat(r.getMembership().getStatus()).isEqualTo(Membership.LifecycleStatus.SUSPENDED.name());
        assertThat(r.getReviewedBy()).isSameAs(reviewer);
        assertThat(r.getReviewedAt()).isNotNull();
    }

    @Test
    void approve_afterCorrectingTheDocument_leavesTheContractPendingForFirstLogin() {
        AffiliationRequest r = pending();
        r.setDocumentNumber("12345679");
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        stubApprovalCreation(r, reviewer);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class)))
                .thenReturn("Contrato de María Pérez, V-12345679");

        service.approve(r.getUuid(), "Aprobada con cédula corregida", reviewer.getUuid());

        verify(termsAcceptanceService, never()).recordSelfServiceAcceptance(any(), any(), any(), any(), any(), any());
        verify(termsAcceptanceService, never()).recordSelfServiceAcceptance(any(), any(), any(), any());
        assertThat(r.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void approve_twice_isRejectedWithoutCreatingAnything() {
        AffiliationRequest r = pending();
        r.setStatus("APPROVED");
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.approve(r.getUuid(), null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.approve.invalid_state");
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
    }

    @Test
    void approve_whenTheDocumentWasRegisteredMeanwhile_isRejected() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
        when(userRepository.findByUuid(reviewer.getUuid())).thenReturn(Optional.of(reviewer));
        when(personRepository.existsByDocumentTypeAndDocumentNumber("V", "12345678")).thenReturn(true);

        assertThatThrownBy(() -> service.approve(r.getUuid(), null, reviewer.getUuid()))
                .hasMessage("affiliation.document.exists");
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
    }

    // ─── Reject ─────────────────────────────────────────────────────────────

    @Test
    void reject_withoutReason_isA422Code() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), "  ", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.reject.reason_required");
        verify(repository, never()).findForUpdateByUuid(any());
    }

    @Test
    void reject_storesTheReasonAndEmailsIt() {
        AffiliationRequest r = pending();
        User reviewer = new User();
        reviewer.setUuid(UUID.randomUUID());
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
        when(userRepository.findByUuid(reviewer.getUuid())).thenReturn(Optional.of(reviewer));
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        service.reject(r.getUuid(), " Documento ilegible ", reviewer.getUuid());

        assertThat(r.getStatus()).isEqualTo("REJECTED");
        assertThat(r.getRejectionReason()).isEqualTo("Documento ilegible");
        ArgumentCaptor<AffiliationRequestEvent> event = ArgumentCaptor.forClass(AffiliationRequestEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AffiliationRequestEvent.Kind.REJECTED);
        assertThat(event.getValue().reason()).isEqualTo("Documento ilegible");
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
    }

    @Test
    void listForAdvisor_withoutAdvisorRecord_is404() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForAdvisor(userUuid, org.springframework.data.domain.Pageable.unpaged(), null))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
        verify(repository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }
}
