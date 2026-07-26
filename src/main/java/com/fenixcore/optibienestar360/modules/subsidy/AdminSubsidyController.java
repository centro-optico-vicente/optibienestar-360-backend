package com.fenixcore.optibienestar360.modules.subsidy;

import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyAuditLogDto;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyCreateRequest;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyDto;
import com.fenixcore.optibienestar360.modules.subsidy.dto.SubsidyUpdateRequest;
import com.fenixcore.optibienestar360.modules.subsidy.service.SubsidiesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import java.util.List;
import java.util.UUID;

/**
 * Admin surface for subsidies & exonerations (v2 PDF item #1, V41). Reads gated
 * by {@code SUBSIDY_VIEW_ALL}; create / modify / revoke by {@code SUBSIDY_APPROVE}
 * (a single permission for all mutations, per the vertical-12 decision). Every
 * mutation is recorded in the immutable audit log, surfaced by {@code /{uuid}/log}.
 */
@RestController
@RequestMapping("/v1/admin/subsidies")
@RequiredArgsConstructor
public class AdminSubsidyController {

    private final SubsidiesService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SUBSIDY_VIEW_ALL')")
    public ResponseEntity<Page<SubsidyDto>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) UUID memberUuid,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.list(pageable, memberUuid, filter, q));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('SUBSIDY_VIEW_ALL')")
    public ResponseEntity<SubsidyDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUBSIDY_APPROVE')")
    public ResponseEntity<SubsidyDto> create(
            @Valid @RequestBody SubsidyCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        SubsidyDto created = service.create(request, actor.getUuid());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('SUBSIDY_APPROVE')")
    public ResponseEntity<SubsidyDto> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody SubsidyUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.update(uuid, request, actor.getUuid()));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('SUBSIDY_APPROVE')")
    public ResponseEntity<Void> revoke(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        service.revoke(uuid, actor.getUuid());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{uuid}/log")
    @PreAuthorize("hasAuthority('SUBSIDY_VIEW_ALL')")
    public ResponseEntity<List<SubsidyAuditLogDto>> getLog(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.getLog(uuid));
    }
}
