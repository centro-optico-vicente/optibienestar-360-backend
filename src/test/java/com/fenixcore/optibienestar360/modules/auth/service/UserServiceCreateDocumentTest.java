package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.mapper.UserMapper;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRoleRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Admin user create: a cédula whose person already has an account is rejected
 * with a clear message before reaching the {@code uniq_users_person} constraint.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceCreateDocumentTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private PersonService personService;

    @InjectMocks private UserService userService;

    @Test
    void createUser_rejectsADocumentThatAlreadyHasAnAccount() {
        Person existing = new Person();
        existing.setId(42L);
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(personService.findByDocument("V", "12345678")).thenReturn(Optional.of(existing));
        when(userRepository.existsByPersonId(42L)).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user.document.has_account");

        verify(personService, never()).findOrCreate(any());
        verify(userRepository, never()).save(any());
    }

    private static AdminCreateUserRequest request() {
        return new AdminCreateUserRequest(
                "new@example.com", "Ana", null, "Pérez", null, "Secret123!",
                "V", "12345678", null, null, null,
                List.of(UUID.randomUUID()), null);
    }
}
