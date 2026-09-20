package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionTierUpdateRequest;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionTiersService;
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
 * Admin CRUD for commission tiers (v2 PDF #5, V42). Granular per V79:
 * {@code COMMISSION_TIER_VIEW_ALL} / {@code _CREATE} / {@code _UPDATE} /
 * {@code _DELETE}.
 */
@RestController
@RequestMapping("/v1/admin/commission-tiers")
@RequiredArgsConstructor
public class AdminCommissionTierController {

    private final CommissionTiersService service;

    @GetMapping
    @PreAuthorize("hasAuthority('COMMISSION_TIER_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<CommissionTierDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID promoterTypeUuid,
            @RequestParam(required = false) UUID campaignUuid,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        Page<CommissionTierDto> page = service.list(pageable, filter, q, promoterTypeUuid, campaignUuid, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMMISSION_TIER_VIEW_ALL')")
    public ResponseEntity<CommissionTierDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('COMMISSION_TIER_CREATE')")
    public ResponseEntity<CommissionTierDto> create(@Valid @RequestBody CommissionTierCreateRequest request) {
        CommissionTierDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMMISSION_TIER_UPDATE')")
    public ResponseEntity<CommissionTierDto> update(@PathVariable UUID uuid,
            @Valid @RequestBody CommissionTierUpdateRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('COMMISSION_TIER_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.getUsage(uuid));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('COMMISSION_TIER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        service.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }
}
