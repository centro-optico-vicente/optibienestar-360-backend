package com.fenixcore.optisaludplus.modules.auth.service;

import com.fenixcore.optisaludplus.core.exception.AuthenticationException;
import com.fenixcore.optisaludplus.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.AdminUpdateUserRequest;
import com.fenixcore.optisaludplus.modules.auth.dto.UserDto;
import com.fenixcore.optisaludplus.modules.auth.entity.Role;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import com.fenixcore.optisaludplus.modules.auth.mapper.UserMapper;
import com.fenixcore.optisaludplus.modules.auth.repository.RoleRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRoleRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService blacklistService;

    // ─── /v1/me ───────────────────────────────────────────────────────────────

    public UserDto getMe(UUID userUuid) {
        User user = userRepository.findWithRolesByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        return userMapper.toDto(user);
    }

    // ─── Admin CRUD ───────────────────────────────────────────────────────────

    public Page<UserDto> listUsers(String filter, Pageable pageable) {
        Specification<User> spec = (filter == null || filter.isBlank())
                ? (root, query, cb) -> null
                : RSQLJPASupport.toSpecification(filter);
        return userRepository.findAll(spec, pageable).map(userMapper::toDto);
    }

    public UserDto getUser(UUID uuid) {
        User user = userRepository.findWithRolesByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + uuid));
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("El correo ya está registrado");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDocumentType(request.documentType());
        user.setDocumentNumber(request.documentNumber());
        user.setPhone(request.phone());
        user.setStatus("ACTIVE");
        userRepository.save(user);

        assignRoles(user, request.roleIds());
        return userMapper.toDto(userRepository.findWithRolesByUuid(user.getUuid()).orElseThrow());
    }

    @Transactional
    public UserDto updateUser(UUID uuid, AdminUpdateUserRequest request) {
        User user = userRepository.findWithRolesByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + uuid));

        if (request.fullName() != null) user.setFullName(request.fullName());
        if (request.documentType() != null) user.setDocumentType(request.documentType());
        if (request.documentNumber() != null) user.setDocumentNumber(request.documentNumber());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.status() != null) user.setStatus(request.status());
        if (request.active() != null) user.setActive(request.active());

        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            deactivateAllRoles(user);
            assignRoles(user, request.roleIds());
        }

        userRepository.save(user);
        return userMapper.toDto(userRepository.findWithRolesByUuid(uuid).orElseThrow());
    }

    @Transactional
    public void deleteUser(UUID uuid) {
        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + uuid));
        user.setActive(false);
        user.setStatus("SUSPENDED");
        userRepository.save(user);
        blacklistService.revokeAllUserRefreshTokens(uuid.toString());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void assignRoles(User user, List<UUID> roleIds) {
        List<UserRole> newRoles = new ArrayList<>();
        for (UUID roleUuid : roleIds) {
            Role role = roleRepository.findByUuid(roleUuid)
                    .orElseThrow(() -> new NoSuchElementException("Rol no encontrado: " + roleUuid));
            UserRole ur = new UserRole();
            ur.setUser(user);
            ur.setRole(role);
            newRoles.add(ur);
        }
        userRoleRepository.saveAll(newRoles);
    }

    private void deactivateAllRoles(User user) {
        List<UserRole> active = userRoleRepository.findByUserIdAndActiveTrue(user.getId());
        active.forEach(ur -> ur.setActive(false));
        userRoleRepository.saveAll(active);
    }
}
