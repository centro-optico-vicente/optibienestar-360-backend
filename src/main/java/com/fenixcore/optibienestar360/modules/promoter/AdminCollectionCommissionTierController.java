package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.promoter.service.CollectionCommissionTiersService;
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
 * Admin CRUD for collection commission tiers (ADR 0013 §3, V44). Granular per
 * V79: {@code COLLECTION_COMMISSION_TIER_VIEW_ALL} / {@code _CREATE} /
 * {@code _UPDATE} / {@code _DELETE}.
 */
@RestController
@RequestMapping("/v1/admin/collection-commission-tiers")
@RequiredArgsConstructor
public class AdminCollectionCommissionTierController {

    private final CollectionCommissionTiersService service;

    @GetMapping
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<CollectionCommissionTierDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID promoterTypeUuid,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Page<CollectionCommissionTierDto> page = service.list(pageable, filter, q, promoterTypeUuid, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_VIEW_ALL')")
    public ResponseEntity<CollectionCommissionTierDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_CREATE')")
    public ResponseEntity<CollectionCommissionTierDto> create(@Valid @RequestBody CollectionCommissionTierCreateRequest request) {
        CollectionCommissionTierDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_UPDATE')")
    public ResponseEntity<CollectionCommissionTierDto> update(@PathVariable UUID uuid,
            @Valid @RequestBody CollectionCommissionTierUpdateRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.getUsage(uuid));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        service.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }
}
