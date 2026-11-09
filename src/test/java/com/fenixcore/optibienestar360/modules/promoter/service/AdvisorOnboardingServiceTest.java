package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.service.UserInvitationService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterWithUserCreateRequest;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link AdvisorOnboardingService}: login + advisor profile in one step (hub ADR 0037 §5). */
@ExtendWith(MockitoExtension.class)
class AdvisorOnboardingServiceTest {

    @Mock private UserService userService;
    @Mock private RoleRepository roleRepository;
    @Mock private PromotersService promotersService;
    @Mock private UserInvitationService invitationService;

    @InjectMocks
    private AdvisorOnboardingService service;

    private final UUID actor = UUID.randomUUID();
    private final UUID userUuid = UUID.randomUUID();
    private Role advisorRole;

    @BeforeEach
    void setUp() {
        advisorRole = new Role();
        advisorRole.setUuid(UUID.randomUUID());
        advisorRole.setName(RoleNames.ADVISOR);
        when(roleRepository.findByName(RoleNames.ADVISOR)).thenReturn(Optional.of(advisorRole));
        UserDto dto = mock(UserDto.class);
        when(dto.uuid()).thenReturn(userUuid);
        when(userService.createUser(any(AdminCreateUserRequest.class), eq(actor))).thenReturn(dto);
        when(promotersService.create(any(PromoterCreateRequest.class))).thenReturn(mock(PromoterDto.class));
    }

    private static PromoterWithUserCreateRequest request(String password, String displayName) {
        return new PromoterWithUserCreateRequest("ana@example.test", "Ana", "Pérez", "V", "12345678",
                "0414-1234567", password, displayName, null, null, null, null, "Caracas");
    }

    @Test
    void createsTheLoginWithTheAdvisorRole_andTheProfileLinkedToIt() {
        service.createWithUser(request("s3cret-pass", null), actor);

        ArgumentCaptor<AdminCreateUserRequest> user = ArgumentCaptor.forClass(AdminCreateUserRequest.class);
        verify(userService).createUser(user.capture(), eq(actor));
        assertThat(user.getValue().roleIds()).containsExactly(advisorRole.getUuid());
        assertThat(user.getValue().defaultRoleUuid()).isEqualTo(advisorRole.getUuid());
        assertThat(user.getValue().password()).isEqualTo("s3cret-pass");

        ArgumentCaptor<PromoterCreateRequest> promoter = ArgumentCaptor.forClass(PromoterCreateRequest.class);
        verify(promotersService).create(promoter.capture());
        assertThat(promoter.getValue().userUuid()).isEqualTo(userUuid);
        assertThat(promoter.getValue().displayName()).isEqualTo("Ana Pérez");
        assertThat(promoter.getValue().assignedZone()).isEqualTo("Caracas");
    }

    @Test
    void withAPassword_noInvitationIsSent() {
        service.createWithUser(request("s3cret-pass", "Ana Ventas"), actor);

        verify(invitationService, never()).invite(any());
    }

    @Test
    void withoutAPassword_theUserIsInvited() {
        service.createWithUser(request(null, "Ana Ventas"), actor);

        verify(invitationService).invite(userUuid);
        ArgumentCaptor<PromoterCreateRequest> promoter = ArgumentCaptor.forClass(PromoterCreateRequest.class);
        verify(promotersService).create(promoter.capture());
        assertThat(promoter.getValue().displayName()).isEqualTo("Ana Ventas");
    }
}
