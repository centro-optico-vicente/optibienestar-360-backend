package com.fenixcore.optibienestar360.modules.affiliation.service;

import com.fenixcore.optibienestar360.core.exception.UnprocessableDetailsException;
import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationDraftStepRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRegistrationDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AssistedAffiliationDraftStartRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequestLog;
import com.fenixcore.optibienestar360.modules.affiliation.event.AffiliationRequestEvent;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestLogRepository;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import com.fenixcore.optibienestar360.modules.person.repository.PersonRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.service.MemberContractService;
import com.fenixcore.optibienestar360.modules.terms.service.MemberTermsApplicant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
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
 * The advisor-assisted affiliation saved as a DRAFT step by step (hub ADR 0037 §5): started without a
 * login, attributed to the advisor, resumable only by that advisor, submitted through the same
 * rules as the person's own registration.
 */
@ExtendWith(MockitoExtension.class)
class AssistedAffiliationDraftsTest {

    private static final String CONTRACT = "Contrato de María Pérez, V-12345678";

    @Mock private AffiliationRequestRepository repository;
    @Mock private AffiliationRequestLogRepository logRepository;
    @Mock private PersonRepository personRepository;
    @Mock private UserRepository userRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberContractService memberContractService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OnboardingPolicyService onboardingPolicyService;

    @InjectMocks
    private AffiliationRequestService service;

    private User advisorUser;
    private Promoter advisor;
    private Plan plan;
    private TermsVersion terms;

    @BeforeEach
    void setUp() {
        advisorUser = new User();
        advisorUser.setId(50L);
        advisorUser.setUuid(UUID.randomUUID());
        advisor = new Promoter();
        advisor.setId(8L);
        advisor.setUuid(UUID.randomUUID());
        advisor.setReferralCode("ABC123");
        advisor.setDisplayName("Ana Asesora");
        plan = new Plan();
        plan.setId(3L);
        plan.setUuid(UUID.randomUUID());
        terms = new TermsVersion();
        terms.setId(9L);
        terms.setUuid(UUID.randomUUID());
    }

    private void asAdvisor() {
        when(promoterRepository.findActiveByUserUuid(advisorUser.getUuid())).thenReturn(Optional.of(advisor));
    }

    private AffiliationRequest draft() {
        AffiliationRequest r = new AffiliationRequest();
        r.setId(11L);
        r.setUuid(UUID.randomUUID());
        r.setChannel(AffiliationRequest.Channel.ASSISTED.name());
        r.setFirstName("María");
        r.setLastName("Pérez");
        r.setDocumentType("V");
        r.setDocumentNumber("12345678");
        r.setEmail("maria@example.com");
        r.setLocale("es");
        r.setPromoter(advisor);
        r.setStatus(AffiliationRequest.Status.DRAFT.name());
        return r;
    }

    private AffiliationRequest completeDraft() {
        AffiliationRequest r = draft();
        r.setBirthDate(LocalDate.of(1985, 3, 15));
        r.setPlan(plan);
        r.setTermsVersion(terms);
        r.setTermsRenderedContent(CONTRACT);
        r.setTermsContentSha256(MemberContractService.sha256(CONTRACT));
        r.setTermsAcceptedAt(Instant.parse("2026-10-20T14:00:00Z"));
        return r;
    }

    private void locked(AffiliationRequest r) {
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
    }

    private static AffiliationDraftStepRequest step(LocalDate birthDate, String phone, String documentNumber, String email) {
        return new AffiliationDraftStepRequest(null, null, birthDate, phone, null, null, null, null, null,
                null, documentNumber, email);
    }

    // ─── Start ──────────────────────────────────────────────────────────────

    @Test
    void start_createsAnAssistedDraft_attributedToTheAdvisor_withoutALogin() {
        asAdvisor();
        when(userRepository.findByUuid(advisorUser.getUuid())).thenReturn(Optional.of(advisorUser));

        AffiliationRegistrationDto dto = service.startAssistedDraft(new AssistedAffiliationDraftStartRequest(
                " María ", "Pérez", "V", "12345678", " Maria@Example.com ", "0414-1234567"),
                advisorUser.getUuid(), new ClientRequestMetadata("200.1.2.3", "Mozilla/5.0"));

        ArgumentCaptor<AffiliationRequest> saved = ArgumentCaptor.forClass(AffiliationRequest.class);
        verify(repository).save(saved.capture());
        AffiliationRequest r = saved.getValue();
        assertThat(r.getStatus()).isEqualTo("DRAFT");
        assertThat(r.isAssisted()).isTrue();
        assertThat(r.getUser()).isNull();
        assertThat(r.getPromoter()).isSameAs(advisor);
        assertThat(r.getReferralCode()).isEqualTo("ABC123");
        assertThat(r.getEmail()).isEqualTo("maria@example.com");
        assertThat(r.getFirstName()).isEqualTo("María");
        assertThat(r.getCreatedBy()).isEqualTo(advisorUser.getUuid());
        assertThat(dto.status()).isEqualTo("DRAFT");
        ArgumentCaptor<AffiliationRequestLog> log = ArgumentCaptor.forClass(AffiliationRequestLog.class);
        verify(logRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo(AffiliationRequestLog.Action.CREATED);
        assertThat(log.getValue().getActor()).isSameAs(advisorUser);
        // The self-registration switch does not apply and staff hear nothing until it is submitted.
        verify(onboardingPolicyService, never()).requireMemberSelfRegistration();
        verify(eventPublisher, never()).publishEvent(any(AffiliationRequestEvent.class));
    }

    @Test
    void start_whenTheCallerIsNotAnAdvisor_is404() {
        when(promoterRepository.findActiveByUserUuid(advisorUser.getUuid())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startAssistedDraft(new AssistedAffiliationDraftStartRequest(
                "María", "Pérez", "V", "12345678", "maria@example.com", null), advisorUser.getUuid(), null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
    }

    @Test
    void start_withARegisteredCedula_isRejected() {
        asAdvisor();
        when(personRepository.existsByDocumentTypeAndDocumentNumber("V", "12345678")).thenReturn(true);

        assertThatThrownBy(() -> service.startAssistedDraft(new AssistedAffiliationDraftStartRequest(
                "María", "Pérez", "V", "12345678", "maria@example.com", null), advisorUser.getUuid(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation.document.exists");
        verify(repository, never()).save(any());
    }

    @Test
    void start_withAnotherOpenDraftOfTheSameCedula_isRejected() {
        asAdvisor();
        lenient().when(repository.existsByDocumentTypeAndDocumentNumberAndStatusAndIdNot("V", "12345678", "DRAFT", -1L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.startAssistedDraft(new AssistedAffiliationDraftStartRequest(
                "María", "Pérez", "V", "12345678", "maria@example.com", null), advisorUser.getUuid(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.document.draft");
    }

    // ─── Steps ──────────────────────────────────────────────────────────────

    @Test
    void personalStep_savesOnTheRequest_withoutALogin() {
        asAdvisor();
        AffiliationRequest r = draft();
        locked(r);

        AffiliationRegistrationDto dto = service.saveAssistedStep(advisorUser.getUuid(), r.getUuid(), "personal",
                step(LocalDate.of(1985, 3, 15), "0414-9999999", null, null), null, null);

        assertThat(r.getBirthDate()).isEqualTo(LocalDate.of(1985, 3, 15));
        assertThat(r.getPhone()).isEqualTo("0414-9999999");
        assertThat(dto.birthDate()).isEqualTo(LocalDate.of(1985, 3, 15));
    }

    @Test
    void personalStep_correctsTheCedulaAndEmail_afterTheSameChecksAsStarting() {
        asAdvisor();
        AffiliationRequest r = draft();
        locked(r);

        service.saveAssistedStep(advisorUser.getUuid(), r.getUuid(), "personal",
                step(null, null, "87654321", "Otra@Example.com"), null, null);

        assertThat(r.getDocumentNumber()).isEqualTo("87654321");
        assertThat(r.getEmail()).isEqualTo("otra@example.com");
        verify(personRepository).existsByDocumentTypeAndDocumentNumber("V", "87654321");
        verify(userRepository).existsByEmail("otra@example.com");
    }

    @Test
    void personalStep_withACedulaThatNowHasAnAccount_isRejected() {
        asAdvisor();
        AffiliationRequest r = draft();
        locked(r);
        when(personRepository.existsByDocumentTypeAndDocumentNumber("V", "87654321")).thenReturn(true);

        assertThatThrownBy(() -> service.saveAssistedStep(advisorUser.getUuid(), r.getUuid(), "personal",
                step(null, null, "87654321", null), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation.document.exists");
        assertThat(r.getDocumentNumber()).isEqualTo("12345678");
    }

    @Test
    void anotherAdvisorsDraft_is404() {
        Promoter other = new Promoter();
        other.setId(99L);
        when(promoterRepository.findActiveByUserUuid(advisorUser.getUuid())).thenReturn(Optional.of(other));
        AffiliationRequest r = draft();  // belongs to `advisor` (id 8)
        locked(r);

        assertThatThrownBy(() -> service.saveAssistedStep(advisorUser.getUuid(), r.getUuid(), "personal", null, null, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("affiliation_request.not_found");
    }

    @Test
    void aSelfServiceRequestOfTheAdvisorsQr_isNotTheirAssistedDraft() {
        asAdvisor();
        AffiliationRequest r = draft();
        r.setChannel(AffiliationRequest.Channel.SELF_SERVICE.name());
        locked(r);

        assertThatThrownBy(() -> service.saveAssistedStep(advisorUser.getUuid(), r.getUuid(), "personal", null, null, null))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("affiliation_request.not_found");
    }

    @Test
    void aSubmittedDraft_cannotBeEditedThroughTheSteps() {
        asAdvisor();
        AffiliationRequest r = completeDraft();
        r.setStatus(AffiliationRequest.Status.PENDING.name());
        locked(r);

        assertThatThrownBy(() -> service.saveAssistedStep(advisorUser.getUuid(), r.getUuid(), "personal", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.draft.invalid_state");
    }

    @Test
    void anUnknownStep_is422() {
        assertThatThrownBy(() -> service.saveAssistedStep(advisorUser.getUuid(), UUID.randomUUID(), "nope", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.step.invalid");
    }

    // ─── Submit ─────────────────────────────────────────────────────────────

    @Test
    void submit_movesTheDraftToPending_logsTheAdvisorAsActor_andAnnouncesItToStaff() {
        asAdvisor();
        AffiliationRequest r = completeDraft();
        locked(r);
        when(userRepository.findByUuid(advisorUser.getUuid())).thenReturn(Optional.of(advisorUser));
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        AffiliationRegistrationDto dto = service.submitAssisted(advisorUser.getUuid(), r.getUuid());

        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getSubmittedAt()).isNotNull();
        assertThat(dto.status()).isEqualTo("PENDING");
        ArgumentCaptor<AffiliationRequestLog> log = ArgumentCaptor.forClass(AffiliationRequestLog.class);
        verify(logRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo(AffiliationRequestLog.Action.SUBMITTED);
        assertThat(log.getValue().getActor()).isSameAs(advisorUser);
        verify(eventPublisher).publishEvent(any(AffiliationRequestEvent.class));
    }

    @Test
    void submit_withoutTheEssentials_is422ListingWhatIsMissing() {
        asAdvisor();
        AffiliationRequest r = draft();
        locked(r);
        when(userRepository.findByUuid(advisorUser.getUuid())).thenReturn(Optional.of(advisorUser));

        assertThatThrownBy(() -> service.submitAssisted(advisorUser.getUuid(), r.getUuid()))
                .isInstanceOfSatisfying(UnprocessableDetailsException.class,
                        ex -> assertThat(ex.getProperties().get("missing"))
                                .isEqualTo(java.util.List.of("birthDate", "plan", "contract")));
        assertThat(r.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void submit_whenAPendingRequestAppearedForTheSameCedula_isRejected() {
        asAdvisor();
        AffiliationRequest r = completeDraft();
        locked(r);
        lenient().when(repository.existsByDocumentTypeAndDocumentNumberAndStatusAndIdNot("V", "12345678", "PENDING", 11L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submitAssisted(advisorUser.getUuid(), r.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.document.pending");
        assertThat(r.getStatus()).isEqualTo("DRAFT");
        verify(eventPublisher, never()).publishEvent(any(AffiliationRequestEvent.class));
    }
}
