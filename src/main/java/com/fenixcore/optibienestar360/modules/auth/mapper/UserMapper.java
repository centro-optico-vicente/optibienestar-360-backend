package com.fenixcore.optibienestar360.modules.auth.mapper;

import com.fenixcore.optibienestar360.modules.auth.dto.RoleDto;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "uuid",              source = "uuid")
    @Mapping(target = "email",             source = "email")

    // Person — sourced via the @OneToOne relationship
    @Mapping(target = "firstName",         source = "person.firstName")
    @Mapping(target = "middleName",        source = "person.middleName")
    @Mapping(target = "lastName",          source = "person.lastName")
    @Mapping(target = "secondLastName",    source = "person.secondLastName")
    @Mapping(target = "fullName",          source = "person.fullName")
    @Mapping(target = "documentType",      source = "person.documentType")
    @Mapping(target = "documentNumber",    source = "person.documentNumber")
    @Mapping(target = "taxDocumentType",   source = "person.taxDocumentType")
    @Mapping(target = "taxDocumentNumber", source = "person.taxDocumentNumber")
    @Mapping(target = "phone",             source = "person.phone")
    @Mapping(target = "locale",            source = "person.locale")

    // Auth
    @Mapping(target = "status",            source = "status")
    @Mapping(target = "active",            source = "active")
    @Mapping(target = "lastLoginAt",       source = "lastLoginAt")
    @Mapping(target = "roles",             expression = "java(mapRoles(user.getUserRoles()))")
    UserDto toDto(User user);

    @Mapping(target = "uuid",        source = "uuid")
    @Mapping(target = "name",        source = "name")
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
