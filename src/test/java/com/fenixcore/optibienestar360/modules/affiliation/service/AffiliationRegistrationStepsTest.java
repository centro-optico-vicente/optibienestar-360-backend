package com.fenixcore.optibienestar360.modules.affiliation.service;

import com.fenixcore.optibienestar360.core.exception.UnprocessableDetailsException;
import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationDraftStepRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRegistrationDto;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequestLog;
import com.fenixcore.optibienestar360.modules.affiliation.event.AffiliationRequestEvent;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestLogRepository;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.repository.PersonRepository;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.service.MemberContractService;
import com.fenixcore.optibienestar360.modules.terms.service.MemberTermsApplicant;
import com.fenixcore.optibienestar360.security.RoleNames;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The step-by-step registration of {@link AffiliationRequestService} (hub ADR
 * 0037 §2): step 1 creates the login and a DRAFT, the steps save into it and
 * "submit" moves it to PENDING.
 */
@ExtendWith(MockitoExtension.class)
class AffiliationRegistrationStepsTest {

    private static final String CONTRACT = "Contrato de María Pérez, V-12345678";

    @Mock private AffiliationRequestRepository repository;
    @Mock private AffiliationRequestLogRepository logRepository;
    @Mock private PersonRepository personRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserService userService;
    @Mock private MemberContractService memberContractService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuthService authService;
    @Mock private OnboardingPolicyService onboardingPolicyService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private AffiliationRequestService service;

    private User user;
    private Plan plan;
    private TermsVersion terms;

    @BeforeEach
    void setUp() {
        Person person = new Person();
        person.setFirstName("María");
        person.setLastName("Pérez");
        user = new User();
        user.setId(5L);
        user.setUuid(UUID.randomUUID());
        user.setEmail("maria@example.com");
        user.setPerson(person);
        user.setUserRoles(new ArrayList<>());

        plan = new Plan();
        plan.setId(3L);
        plan.setUuid(UUID.randomUUID());
        plan.setName("Plan Individual");
        terms = new TermsVersion();
        terms.setId(9L);
        terms.setUuid(UUID.randomUUID());
    }

    private AffiliationRequest draft() {
        AffiliationRequest r = new AffiliationRequest();
        r.setId(11L);
        r.setUuid(UUID.randomUUID());
        r.setFirstName("María");
        r.setLastName("Pérez");
        r.setDocumentType("V");
        r.setDocumentNumber("12345678");
        r.setEmail("maria@example.com");
        r.setLocale("es");
        r.setUser(user);
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

    private void ownDraft(AffiliationRequest r) {
        when(repository.findFirstByUser_UuidOrderByCreatedAtDesc(user.getUuid())).thenReturn(Optional.of(r));
        when(repository.findForUpdateByUuid(r.getUuid())).thenReturn(Optional.of(r));
    }

    private static AffiliationRequestCreateRequest stepOne() {
        return new AffiliationRequestCreateRequest("María", "Pérez", "V", "12345678", null,
                " Maria@Example.com ", "0414-1234567", null, null, null, null, null);
    }

    // ─── Step 1 ─────────────────────────────────────────────────────────────

    @Test
    void stepOne_createsTheLoginAndADraft_andOpensTheSession() {
        Role pending = new Role();
        pending.setId(7L);
        pending.setUuid(UUID.randomUUID());
        pending.setName(RoleNames.MEMBER_PENDING);
        when(roleRepository.findByName(RoleNames.MEMBER_PENDING)).thenReturn(Optional.of(pending));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        UserDto dto = mock(UserDto.class);
        when(dto.uuid()).thenReturn(user.getUuid());
        when(userService.createUserWithPasswordHash(any(AdminCreateUserRequest.class), eq("hash"), isNull()))
                .thenReturn(dto);
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        UserRole assignment = new UserRole();
        assignment.setRole(pending);
        when(userRoleRepository.findByUserIdAndRoleId(5L, 7L)).thenReturn(Optional.of(assignment));
        LoginResponse session = mock(LoginResponse.class);
        when(authService.openSession(user, httpRequest)).thenReturn(session);

        AffiliationRequestCreatedDto created = service.startRegistration(stepOne(),
                new ClientRequestMetadata("200.1.2.3", "Mozilla/5.0"), httpRequest);

        ArgumentCaptor<AffiliationRequest> saved = ArgumentCaptor.forClass(AffiliationRequest.class);
        verify(repository).save(saved.capture());
        AffiliationRequest r = saved.getValue();
        assertThat(r.getStatus()).isEqualTo("DRAFT");
        assertThat(r.getUser()).isSameAs(user);
        assertThat(r.getEmail()).isEqualTo("maria@example.com");
        assertThat(r.getPasswordHash()).isNull();
        assertThat(r.getPlan()).isNull();
        assertThat(created.session()).isSameAs(session);
        assertThat(user.getUserRoles()).containsExactly(assignment);
        ArgumentCaptor<AffiliationRequestLog> log = ArgumentCaptor.forClass(AffiliationRequestLog.class);
        verify(logRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo(AffiliationRequestLog.Action.CREATED);
        // Nothing is announced to staff until the request is submitted.
        verify(eventPublisher, never()).publishEvent(any(AffiliationRequestEvent.class));
    }

    @Test
    void stepOne_withAnExistingCedula_is422AccountExists() {
        when(personRepository.existsByDocumentTypeAndDocumentNumber("V", "12345678")).thenReturn(true);

        assertThatThrownBy(() -> service.startRegistration(stepOne(), null, httpRequest))
                .isInstanceOfSatisfying(UnprocessableDetailsException.class,
                        ex -> assertThat(ex.getProperties()).containsEntry("code", "ACCOUNT_EXISTS"));
        verify(repository, never()).save(any());
    }

    @Test
    void stepOne_withAnExistingEmail_is422AccountExists() {
        when(userRepository.existsByEmail("maria@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.startRegistration(stepOne(), null, httpRequest))
                .isInstanceOfSatisfying(UnprocessableDetailsException.class,
                        ex -> assertThat(ex.getProperties()).containsEntry("code", "ACCOUNT_EXISTS"));
    }

    // ─── Steps ──────────────────────────────────────────────────────────────

    @Test
    void personalStep_savesOnTheRequestAndOnThePerson() {
        AffiliationRequest r = draft();
        ownDraft(r);

        AffiliationRegistrationDto dto = service.saveStep(user.getUuid(), "personal",
                new AffiliationDraftStepRequest(null, null, LocalDate.of(1985, 3, 15), "0414-9999999",
                        null, null, null, null, null, null, null, null), null, null);

        assertThat(r.getBirthDate()).isEqualTo(LocalDate.of(1985, 3, 15));
        assertThat(r.getPhone()).isEqualTo("0414-9999999");
        assertThat(user.getPerson().getBirthDate()).isEqualTo(LocalDate.of(1985, 3, 15));
        assertThat(dto.birthDate()).isEqualTo(LocalDate.of(1985, 3, 15));
        assertThat(dto.contractAccepted()).isFalse();
    }

    @Test
    void anUnknownStep_is422() {
        assertThatThrownBy(() -> service.saveStep(user.getUuid(), "nope", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.step.invalid");
    }

    @Test
    void aSubmittedRequest_cannotBeEditedThroughTheSteps() {
        AffiliationRequest r = completeDraft();
        r.setStatus(AffiliationRequest.Status.PENDING.name());
        ownDraft(r);

        assertThatThrownBy(() -> service.saveStep(user.getUuid(), "personal", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("affiliation_request.draft.invalid_state");
    }

    // ─── Submit ─────────────────────────────────────────────────────────────

    @Test
    void submit_movesTheDraftToPending_logsIt_andAnnouncesItToStaff() {
        AffiliationRequest r = completeDraft();
        ownDraft(r);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class))).thenReturn(CONTRACT);

        AffiliationRegistrationDto dto = service.submit(user.getUuid());

        assertThat(r.getStatus()).isEqualTo("PENDING");
        assertThat(r.getSubmittedAt()).isNotNull();
        assertThat(dto.status()).isEqualTo("PENDING");
        ArgumentCaptor<AffiliationRequestLog> log = ArgumentCaptor.forClass(AffiliationRequestLog.class);
        verify(logRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo(AffiliationRequestLog.Action.SUBMITTED);
        assertThat(log.getValue().getFromStatus()).isEqualTo("DRAFT");
        assertThat(log.getValue().getToStatus()).isEqualTo("PENDING");
        ArgumentCaptor<AffiliationRequestEvent> event = ArgumentCaptor.forClass(AffiliationRequestEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().kind()).isEqualTo(AffiliationRequestEvent.Kind.RECEIVED);
    }

    @Test
    void submit_withoutTheEssentials_is422AndListsWhatIsMissing() {
        AffiliationRequest r = draft();
        ownDraft(r);

        assertThatThrownBy(() -> service.submit(user.getUuid()))
                .isInstanceOfSatisfying(UnprocessableDetailsException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("affiliation_request.submit.incomplete");
                    assertThat(ex.getProperties().get("missing"))
                            .isEqualTo(java.util.List.of("birthDate", "plan", "contract"));
                });
        assertThat(r.getStatus()).isEqualTo("DRAFT");
        verify(eventPublisher, never()).publishEvent(any(AffiliationRequestEvent.class));
    }

    @Test
    void submit_whenTheContractTextChanged_asksToAcceptAgain() {
        AffiliationRequest r = completeDraft();
        ownDraft(r);
        when(memberContractService.render(eq(terms), any(MemberTermsApplicant.class)))
                .thenReturn(CONTRACT + " (corregido)");

        assertThatThrownBy(() -> service.submit(user.getUuid()))
                .isInstanceOfSatisfying(UnprocessableDetailsException.class,
                        ex -> assertThat(ex.getProperties().get("missing")).isEqualTo(java.util.List.of("contract")));
    }
}
