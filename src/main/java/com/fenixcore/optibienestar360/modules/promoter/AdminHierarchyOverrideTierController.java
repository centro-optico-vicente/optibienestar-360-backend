package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.HierarchyOverrideTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.HierarchyOverrideTiersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Admin CRUD for hierarchy-override bands (V102, hub plan §2, V108
 * permissions) — Supervisor/Coordinador override percentages, previously
 * configurable only via the migration seed.
 */
@RestController
@RequestMapping("/v1/admin/hierarchy-override-tiers")
@RequiredArgsConstructor
public class AdminHierarchyOverrideTierController {

    private final HierarchyOverrideTiersService service;

    @GetMapping
    @PreAuthorize("hasAuthority('HIERARCHY_OVERRIDE_TIER_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<HierarchyOverrideTierDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID rankUuid,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Page<HierarchyOverrideTierDto> page = service.list(pageable, filter, q, rankUuid, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('HIERARCHY_OVERRIDE_TIER_VIEW_ALL')")
    public ResponseEntity<HierarchyOverrideTierDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('HIERARCHY_OVERRIDE_TIER_CREATE')")
    public ResponseEntity<HierarchyOverrideTierDto> create(@Valid @RequestBody HierarchyOverrideTierCreateRequest request) {
        HierarchyOverrideTierDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('HIERARCHY_OVERRIDE_TIER_UPDATE')")
    public ResponseEntity<HierarchyOverrideTierDto> update(@PathVariable UUID uuid,
            @Valid @RequestBody HierarchyOverrideTierUpdateRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('HIERARCHY_OVERRIDE_TIER_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.getUsage(uuid));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('HIERARCHY_OVERRIDE_TIER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        service.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }
}
