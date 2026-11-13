package com.fenixcore.optibienestar360.modules.allyrequest.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.MyAllyRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.onboarding.service.OnboardingPolicyService;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AllyRequestService#createForAccount}: someone who already has an
 * account (here a member) registers an ally without a second account — the
 * ally is held by their own person, owned by their login, and the login gains
 * {@code ALIADO_PENDIENTE} next to the roles it has.
 */
@ExtendWith(MockitoExtension.class)
class AllyRequestForAccountTest {

    @Mock private AlliesService alliesService;
    @Mock private AllyRepository allyRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private UserService userService;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private OnboardingPolicyService onboardingPolicyService;

    @InjectMocks
    private AllyRequestService service;

    private User user;
    private Person person;
    private Role memberRole;
    private Role pendingRole;

    @BeforeEach
    void setUp() {
        person = new Person();
        person.setId(40L);
        person.setFirstName("María");
        person.setLastName("Pérez");
        person.setDocumentType("V");
        person.setDocumentNumber("12345678");
        person.setPhone("0414-1234567");
        user = new User();
        user.setId(5L);
        user.setUuid(UUID.randomUUID());
        user.setEmail("maria@example.com");
        user.setPerson(person);
        user.setUserRoles(new ArrayList<>());
        memberRole = new Role();
        memberRole.setId(1L);
        memberRole.setName(RoleNames.MEMBER);
        UserRole member = new UserRole();
        member.setRole(memberRole);
        member.setActive(true);
        user.getUserRoles().add(member);
        user.setDefaultRole(memberRole);
        pendingRole = new Role();
        pendingRole.setId(9L);
        pendingRole.setUuid(UUID.randomUUID());
        pendingRole.setName(RoleNames.ALLY_PENDING);
    }

    private static MyAllyRequestCreateRequest b2b(Boolean acceptedPortalTerms) {
        return new MyAllyRequestCreateRequest(Segment.B2B, null, "Óptica María", null, null, null, null, null, null,
                null, acceptedPortalTerms, null);
    }

    @Test
    void createsTheAllyOnTheCallersPersonAndAddsThePendingRole() {
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(roleRepository.findByName(RoleNames.ALLY_PENDING)).thenReturn(Optional.of(pendingRole));
        Ally ally = new Ally();
        ally.setId(30L);
        ally.setUuid(UUID.randomUUID());
        ally.setSegment(Segment.B2B);
        ally.setRegistrationStatus(Ally.RegistrationStatus.PENDING);
        AllyDetailDto created = mock(AllyDetailDto.class);
        when(created.uuid()).thenReturn(ally.getUuid());
        when(alliesService.createRequest(any(AllyCreateRequest.class), eq(Ally.RegistrationChannel.SELF_SERVICE),
                isNull())).thenReturn(created);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        AllyRequestCreatedDto result = service.createForAccount(user.getUuid(), b2b(true));

        ArgumentCaptor<AllyCreateRequest> allyReq = ArgumentCaptor.forClass(AllyCreateRequest.class);
        verify(alliesService).createRequest(allyReq.capture(), any(), isNull());
        assertThat(allyReq.getValue().holder().documentNumber()).isEqualTo("12345678");
        assertThat(allyReq.getValue().email()).isEqualTo("maria@example.com");
        assertThat(result.allyUuid()).isEqualTo(ally.getUuid());
        assertThat(result.email()).isEqualTo("maria@example.com");
        // No second account; the member role stays the default one.
        verify(userService, never()).createUserWithPasswordHash(any(), any(), any());
        assertThat(user.getUserRoles()).extracting(ur -> ur.getRole().getName())
                .containsExactlyInAnyOrder(RoleNames.MEMBER, RoleNames.ALLY_PENDING);
        assertThat(user.getDefaultRole()).isSameAs(memberRole);
        ArgumentCaptor<AllyUser> link = ArgumentCaptor.forClass(AllyUser.class);
        verify(allyUserRepository).save(link.capture());
        assertThat(link.getValue().getUser()).isSameAs(user);
        assertThat(link.getValue().getAllyRole()).isEqualTo(AllyUser.AllyRole.OWNER);
    }

    @Test
    void withoutAcceptingThePortalTerms_is422() {
        assertThatThrownBy(() -> service.createForAccount(user.getUuid(), b2b(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_request.terms.not_accepted");
        verify(alliesService, never()).createRequest(any(), any(), any());
    }

    @Test
    void anAccountWithoutPhoneOrMobile_is422() {
        person.setPhone(null);
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.createForAccount(user.getUuid(), b2b(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_request.mobile.required");
        verify(alliesService, never()).createRequest(any(), any(), any());
    }
}
