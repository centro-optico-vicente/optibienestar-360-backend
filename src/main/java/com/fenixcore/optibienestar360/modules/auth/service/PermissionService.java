package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.modules.auth.dto.PermissionDomainDto;
import com.fenixcore.optibienestar360.modules.auth.dto.PermissionDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Permission;
import com.fenixcore.optibienestar360.modules.auth.entity.PermissionDomain;
import com.fenixcore.optibienestar360.modules.auth.repository.PermissionDomainRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionDomainRepository permissionDomainRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Two queries (domains; permissions with {@code JOIN FETCH} on domain) —
     * avoids N+1 when grouping by permission.domain.id below.
     */
    public List<PermissionDomainDto> getCatalog() {
        Map<Long, List<PermissionDto>> permissionsByDomainId = permissionRepository
                .findAllActiveOrderedForCatalog()
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getDomain().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(PermissionService::toPermissionDto, Collectors.toList())
                ));

        return permissionDomainRepository.findAllByActiveTrueOrderByDisplayOrder()
                .stream()
                .map(domain -> toDomainDto(domain, permissionsByDomainId.getOrDefault(domain.getId(), List.of())))
                .toList();
    }

    private static PermissionDomainDto toDomainDto(PermissionDomain domain, List<PermissionDto> permissions) {
        return new PermissionDomainDto(
                domain.getUuid(),
                domain.getCode(),
                domain.getName(),
                domain.getIcon(),
                domain.getDescription(),
                domain.getDisplayOrder(),
                permissions
        );
    }

    private static PermissionDto toPermissionDto(Permission permission) {
        // Permission entity currently has only one user-facing string (description).
        // We expose it as `name` (the short label for the checkbox); `description`
        // is reserved for a future longer help-text column.
        return new PermissionDto(
                permission.getUuid(),
                permission.getDescription(),
                null
        );
    }
}
