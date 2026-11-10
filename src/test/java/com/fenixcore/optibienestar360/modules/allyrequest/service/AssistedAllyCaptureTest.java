package com.fenixcore.optibienestar360.modules.allyrequest.service;

import com.fenixcore.optibienestar360.core.exception.UnprocessableDetailsException;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AllyDraftAccess;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AssistedAllyDraftStartRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.event.AllyRequestEvent;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Advisor-assisted ally capture (hub ADR 0037 §5): started without a login and attributed to the advisor,
 * resumable only by that advisor, and on submit the contact person gets their login and the onboarding policy
 * applies — regardless of the self-registration switch.
 */
@ExtendWith(MockitoExtension.class)
class AssistedAllyCaptureTest {

    @Mock private AlliesService alliesService;
    @Mock private AllyRepository allyRepository;
    @Mock private AllyAgreementRepository agreementRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private PersonService personService;
    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuthService authService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OnboardingPolicyService onboardingPolicyService;
    @Mock private AllyDraftAccess draftAccess;

    @InjectMocks
    private AllyRequestService service;

    private final UUID advisorUserUuid = UUID.randomUUID();
    private Promoter advisor;

    @BeforeEach
    void setUp() {
        advisor = new Promoter();
        advisor.setId(8L);
        advisor.setUuid(UUID.randomUUID());
        advisor.setReferralCode("ABC123");
        advisor.setDisplayName("Ana Asesora");
    }

    private AssistedAllyDraftStartRequest startRequest() {
        return new AssistedAllyDraftStartRequest(Segment.B2B, null, "Óptica Central", null, "Luis", "Pérez", "V",
                "12345678", "04141234567", "luis@example.com", null, "10% de descuento");
    }

    @Test
    void startAssistedDraft_createsAnAdvisorChannelRequestAttributedToTheAdvisor_withoutLoginNorSwitchCheck() {
        when(promoterRepository.findActiveByUserUuid(advisorUserUuid)).thenReturn(Optional.of(advisor));
        when(personService.findByDocument("V", "12345678")).thenReturn(Optional.empty());
        AllyDetailDto created = mock(AllyDetailDto.class);
        UUID allyUuid = UUID.randomUUID();
        when(created.uuid()).thenReturn(allyUuid);
        when(alliesService.createRequest(any(AllyCreateRequest.class), eq(Ally.RegistrationChannel.ADVISOR),
                eq("10% de descuento"))).thenReturn(created);

        AllyRequestCreatedDto result = service.startAssistedDraft(startRequest(), advisorUserUuid);

        assertThat(result.allyUuid()).isEqualTo(allyUuid);
        assertThat(result.registrationStatus()).isEqualTo(Ally.RegistrationStatus.PENDING);
        assertThat(result.promoterName()).isEqualTo("Ana Asesora");
        assertThat(result.session()).isNull();
        ArgumentCaptor<AllyCreateRequest> sent = ArgumentCaptor.forClass(AllyCreateRequest.class);
        verify(alliesService).createRequest(sent.capture(), eq(Ally.RegistrationChannel.ADVISOR), any());
        assertThat(sent.getValue().capturedByPromoterUuid()).isEqualTo(advisor.getUuid());
        assertThat(sent.getValue().holder().documentNumber()).isEqualTo("12345678");
        verify(onboardingPolicyService, never()).requireAllySelfRegistration();
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
    }

    @Test
    void startAssistedDraft_withAnEmailThatAlreadyHasAnAccount_isRefused() {
        when(promoterRepository.findActiveByUserUuid(advisorUserUuid)).thenReturn(Optional.of(advisor));
        when(userRepository.existsByEmail("luis@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.startAssistedDraft(startRequest(), advisorUserUuid))
                .isInstanceOf(UnprocessableDetailsException.class)
                .hasMessage("ally_request.email.taken");
        verify(alliesService, never()).createRequest(any(), any(), any());
    }

    @Test
    void startAssistedDraft_withoutAdvisorRecord_isNotFound() {
        when(promoterRepository.findActiveByUserUuid(advisorUserUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startAssistedDraft(startRequest(), advisorUserUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
    }

    @Test
    void submitAssisted_ofAnotherAdvisorsDraft_isNotFound() {
        UUID allyUuid = UUID.randomUUID();
        when(draftAccess.requireOwnDraft(allyUuid, advisorUserUuid))
                .thenThrow(new NoSuchElementException("ally_request.not_found"));

        assertThatThrownBy(() -> service.submitAssisted(advisorUserUuid, allyUuid))
                .isInstanceOf(NoSuchElementException.class);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    private Ally draftAlly() {
        Ally ally = new Ally();
        ally.setId(30L);
        ally.setUuid(UUID.randomUUID());
        ally.setName("Óptica Central");
        ally.setSegment(Segment.B2B);
        ally.setRegistrationChannel(Ally.RegistrationChannel.ADVISOR);
        ally.setRegistrationStatus(Ally.RegistrationStatus.PENDING);
        ally.setCapturedByPromoter(advisor);
        Person holder = new Person();
        holder.setId(5L);
        holder.setFirstName("Luis");
        holder.setLastName("Pérez");
        holder.setDocumentType("V");
        holder.setDocumentNumber("12345678");
        holder.setEmail("luis@example.com");
        ally.setHolderPerson(holder);
        ally.setEmail("luis@example.com");
        return ally;
    }

    @Test
    void submitAssisted_withMissingEssentials_answersTheMissingKeysAndCreatesNoLogin() {
        Ally ally = draftAlly();
        when(draftAccess.requireOwnDraft(ally.getUuid(), advisorUserUuid)).thenReturn(ally);
        when(allyRepository.findForUpdateByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        assertThatThrownBy(() -> service.submitAssisted(advisorUserUuid, ally.getUuid()))
                .isInstanceOfSatisfying(UnprocessableDetailsException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("ally_request.submit.incomplete");
                    assertThat(ex.getProperties().get("missing")).isEqualTo(List.of("allyType", "city", "taxDocument"));
                });
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
        assertThat(ally.getRegistrationSubmittedAt()).isNull();
    }

    @Test
    void submitAssisted_createsTheContactLogin_invitesIt_andSendsTheRequestToReview() {
        Ally ally = draftAlly();
        AllyType type = new AllyType();
        type.setId(1L);
        type.setUuid(UUID.randomUUID());
        ally.getAllyTypes().add(type);
        City city = new City();
        city.setUuid(UUID.randomUUID());
        city.setName("Caracas");
        ally.setCity(city);
        ally.setTaxDocumentType("J");
        ally.setTaxDocumentNumber("123456789");
        when(draftAccess.requireOwnDraft(ally.getUuid(), advisorUserUuid)).thenReturn(ally);
        when(allyRepository.findForUpdateByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        Role pending = new Role();
        pending.setUuid(UUID.randomUUID());
        pending.setName(RoleNames.ALLY_PENDING);
        when(roleRepository.findByName(RoleNames.ALLY_PENDING)).thenReturn(Optional.of(pending));
        UserDto dto = mock(UserDto.class);
        UUID userUuid = UUID.randomUUID();
        when(dto.uuid()).thenReturn(userUuid);
        when(userService.createUserWithPasswordHash(any(), any(), any())).thenReturn(dto);
        User login = new User();
        login.setId(77L);
        login.setUuid(userUuid);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(login));
        when(onboardingPolicyService.autoApprovesAlly()).thenReturn(false);

        AllyRequestCreatedDto result = service.submitAssisted(advisorUserUuid, ally.getUuid());

        assertThat(ally.getRegistrationSubmittedAt()).isNotNull();
        assertThat(result.registrationStatus()).isEqualTo(Ally.RegistrationStatus.PENDING);
        verify(allyUserRepository).save(any(AllyUser.class));
        verify(authService).issuePasswordSetupLink(eq(login), any(), any());
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).extracting(e -> ((AllyRequestEvent) e).kind())
                .containsExactly(AllyRequestEvent.Kind.INVITED, AllyRequestEvent.Kind.RECEIVED);
        verify(onboardingPolicyService, never()).requireAllySelfRegistration();
    }
}
