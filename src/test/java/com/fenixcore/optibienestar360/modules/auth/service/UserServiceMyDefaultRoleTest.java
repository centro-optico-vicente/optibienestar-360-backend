package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.modules.auth.dto.AdminUpdateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.MyRoleDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Self-service default role ({@code PUT /v1/me/default-role}): only a role the
 * user already holds, never a new assignment, and the selector lists the
 * default first.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceMyDefaultRoleTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private PersonService personService;

    @InjectMocks private UserService userService;

    private final UUID userUuid = UUID.randomUUID();

    @Test
    void setMyDefaultRole_setsAHeldRole() {
        Role admin = role(1L, RoleNames.ADMINISTRATOR);
        Role advisor = role(2L, "ASESOR");
        User user = userWith(effective(admin), effective(advisor));
        when(userRepository.findWithRolesByUuid(userUuid)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.setMyDefaultRole(userUuid, advisor.getUuid());

        assertThat(user.getDefaultRole()).isSameAs(advisor);
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void setMyDefaultRole_rejectsARoleTheUserDoesNotHold() {
        Role admin = role(1L, RoleNames.ADMINISTRATOR);
        User user = userWith(effective(admin));
        when(userRepository.findWithRolesByUuid(userUuid)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.setMyDefaultRole(userUuid, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.default_role.not_held");

        verify(userRepository, never()).save(any());
        verify(userRoleRepository, never()).save(any());
    }

    @Test
    void setMyDefaultRole_rejectsAnInactiveAssignment() {
        Role admin = role(1L, RoleNames.ADMINISTRATOR);
        Role advisor = role(2L, "ASESOR");
        UserRole revoked = effective(advisor);
        revoked.setActive(false);
        User user = userWith(effective(admin), revoked);
        when(userRepository.findWithRolesByUuid(userUuid)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.setMyDefaultRole(userUuid, advisor.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.default_role.not_held");
    }

    @Test
    void getMyEffectiveRoles_listsTheDefaultFirstAndSkipsAStaleDefault() {
        Role admin = role(1L, RoleNames.ADMINISTRATOR);
        Role advisor = role(2L, "ASESOR");
        User user = userWith(effective(admin), effective(advisor));
        user.setDefaultRole(advisor);
        when(userRepository.findWithRolesByUuid(userUuid)).thenReturn(Optional.of(user));

        List<MyRoleDto> roles = userService.getMyEffectiveRoles(userUuid);

        assertThat(roles).extracting(MyRoleDto::name).containsExactly("ASESOR", RoleNames.ADMINISTRATOR);
        assertThat(roles).extracting(MyRoleDto::isDefault).containsExactly(true, false);

        // A default role the user no longer holds is not offered.
        user.setDefaultRole(role(3L, "OPERADOR"));
        assertThat(userService.getMyEffectiveRoles(userUuid)).extracting(MyRoleDto::name)
                .containsExactly(RoleNames.ADMINISTRATOR, "ASESOR");
    }

    @Test
    void updateUser_droppingTheSignInRoleClearsTheDefault() {
        Role admin = role(1L, RoleNames.ADMINISTRATOR);
        Role advisor = role(2L, "ASESOR");
        UserRole adminPivot = effective(admin);
        UserRole advisorPivot = effective(advisor);
        User user = userWith(adminPivot, advisorPivot);
        user.setDefaultRole(advisor);

        UUID actorUuid = UUID.randomUUID();
        User actor = new User();
        actor.setUuid(actorUuid);
        actor.setUserRoles(new ArrayList<>(List.of(effective(role(3L, RoleNames.ADMINISTRATOR)))));

        when(userRepository.findWithRolesByUuid(userUuid)).thenReturn(Optional.of(user));
        when(userRepository.findWithRolesByUuid(actorUuid)).thenReturn(Optional.of(actor));
        when(roleRepository.findByUuid(admin.getUuid())).thenReturn(Optional.of(admin));
        when(userRoleRepository.findByUserId(user.getId())).thenReturn(List.of(adminPivot, advisorPivot));

        // Keep only ADMINISTRATOR: ASESOR is removed, so it can no longer be the sign-in role.
        AdminUpdateUserRequest request = new AdminUpdateUserRequest(null, null, null, null, null, null, null, null,
                null, null, null, null, List.of(admin.getUuid()), null);
        userService.updateUser(userUuid, request, actorUuid);

        assertThat(advisorPivot.isActive()).isFalse();
        assertThat(user.getDefaultRole()).isNull();
    }

    private User userWith(UserRole... assignments) {
        User user = new User();
        user.setUuid(userUuid);
        List<UserRole> list = new ArrayList<>();
        for (UserRole ur : assignments) {
            ur.setUser(user);
            list.add(ur);
        }
        user.setUserRoles(list);
        return user;
    }

    private static UserRole effective(Role role) {
        UserRole ur = new UserRole();
        ur.setRole(role);
        ur.setActive(true);
        return ur;
    }

    private static Role role(Long id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setUuid(UUID.randomUUID());
        role.setName(name);
        return role;
    }
}
