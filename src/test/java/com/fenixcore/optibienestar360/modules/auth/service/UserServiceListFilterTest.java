package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code ?filter=}/{@code ?q=} wiring on
 * {@link UserService#listUsers(String, String, Pageable, UUID)}: the
 * {@code person.fullName} whitelist fix (was bare {@code fullName}, which
 * doesn't exist on {@link User} and blew up as a 500 against Hibernate) and
 * the new free-text {@code ?q=} search.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceListFilterTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private PersonService personService;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private UserService userService;

    private static final UUID ACTOR_UUID = UUID.randomUUID();

    private UserService newService() {
        lenient().when(defaultSortResolver.withDefaultSortIfUnsorted(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));
        return new UserService(userRepository, roleRepository, userRoleRepository,
                userMapper, passwordEncoder, blacklistService, personService,
                allyUserRepository, promoterRepository, defaultSortResolver);
    }

    @Test
    void rejectsBareFullNameField() {
        userService = newService();
        assertThatThrownBy(() -> userService.listUsers("fullName=='*yef*'", null,
                false, PageRequest.of(0, 10), ACTOR_UUID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.filter.field_not_allowed");
    }

    @Test
    void acceptsPersonFullNameField() {
        userService = newService();
        lenient().when(userRepository.findWithRolesByUuid(ACTOR_UUID)).thenReturn(Optional.empty());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<User>(java.util.List.of()));

        Page<?> result = userService.listUsers("person.fullName=='*yef*'", null,
                false, PageRequest.of(0, 10), ACTOR_UUID);

        assertThat(result).isNotNull();
    }

    @Test
    void supportsFreeTextSearch() {
        userService = newService();
        lenient().when(userRepository.findWithRolesByUuid(ACTOR_UUID)).thenReturn(Optional.empty());
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<User>(java.util.List.of()));

        Page<?> result = userService.listUsers(null, "yef", false, PageRequest.of(0, 10), ACTOR_UUID);

        assertThat(result).isNotNull();
    }
}
