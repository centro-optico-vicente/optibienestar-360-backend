package com.fenixcore.optisaludplus.modules.auth.mapper;

import com.fenixcore.optisaludplus.modules.auth.dto.RoleDto;
import com.fenixcore.optisaludplus.modules.auth.dto.UserDto;
import com.fenixcore.optisaludplus.modules.auth.entity.Role;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.entity.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "uuid", source = "uuid")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "documentType", source = "documentType")
    @Mapping(target = "documentNumber", source = "documentNumber")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "lastLoginAt", source = "lastLoginAt")
    @Mapping(target = "roles", expression = "java(mapRoles(user.getUserRoles()))")
    UserDto toDto(User user);

    @Mapping(target = "uuid", source = "uuid")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "description", source = "description")
    RoleDto roleToDto(Role role);

    default List<RoleDto> mapRoles(List<UserRole> userRoles) {
        if (userRoles == null) return List.of();
        return userRoles.stream()
                .filter(UserRole::isActive)
                .map(ur -> roleToDto(ur.getRole()))
                .distinct()
                .toList();
    }
}
