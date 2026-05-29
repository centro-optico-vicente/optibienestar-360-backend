package com.fenixcore.optisaludplus.modules.auth.service;

import com.fenixcore.optisaludplus.modules.auth.dto.RoleDto;
import com.fenixcore.optisaludplus.modules.auth.entity.Role;
import com.fenixcore.optisaludplus.modules.auth.mapper.UserMapper;
import com.fenixcore.optisaludplus.modules.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    public List<RoleDto> listActiveRoles() {
        return roleRepository.findAllByActiveTrue().stream()
            .map(userMapper::roleToDto)
            .toList()
        ;
    }

    public RoleDto getRole(UUID uuid) {
        Role role = roleRepository.findByUuid(uuid)
            .orElseThrow(() -> new NoSuchElementException("Rol no encontrado"))
        ;
        return userMapper.roleToDto(role);
    }
}
