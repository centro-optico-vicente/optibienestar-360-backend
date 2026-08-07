package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionCommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.CollectionCommissionTiersService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
 * Admin CRUD for collection commission tiers (ADR 0013 §3, V44). All operations
 * gated by {@code COLLECTION_COMMISSION_TIER_MANAGE}.
 */
@RestController
@RequestMapping("/v1/admin/collection-commission-tiers")
@RequiredArgsConstructor
public class AdminCollectionCommissionTierController {

    private final CollectionCommissionTiersService service;

    @GetMapping
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_MANAGE')")
    public ResponseEntity<Page<CollectionCommissionTierDto>> list(
            @PageableDefault(size = 50, sort = "maxDays", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(service.list(pageable, filter, q, includeInactive));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_MANAGE')")
    public ResponseEntity<CollectionCommissionTierDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_MANAGE')")
    public ResponseEntity<CollectionCommissionTierDto> create(@Valid @RequestBody CollectionCommissionTierCreateRequest request) {
        CollectionCommissionTierDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_MANAGE')")
    public ResponseEntity<CollectionCommissionTierDto> update(@PathVariable UUID uuid,
            @Valid @RequestBody CollectionCommissionTierUpdateRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COLLECTION_COMMISSION_TIER_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
