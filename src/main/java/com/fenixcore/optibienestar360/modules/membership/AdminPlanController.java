package com.fenixcore.optibienestar360.modules.membership;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanCreateRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanUpdateRequest;
import com.fenixcore.optibienestar360.modules.membership.service.PlansService;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD over plans. The catalog is small (3 SKUs currently — Individual,
 * Familiar, Corporativo) so the listing default returns a high page size
 * sorted alphabetically; specialized filtering via RSQL stays available.
 */
@RestController
@RequestMapping("/v1/admin/plans")
@RequiredArgsConstructor
public class AdminPlanController {

    private final PlansService plansService;

    @GetMapping
    @PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<PlanDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<PlanDto> page = plansService.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, plansService.effectiveSort(pageable)));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
    public ResponseEntity<List<OptionDto>> options(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues) {
        return ResponseEntity.ok(plansService.listOptions(q, limit, currentValues));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
    public ResponseEntity<PlanDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(plansService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PLAN_CREATE')")
    public ResponseEntity<PlanDto> create(@Valid @RequestBody PlanCreateRequest request) {
        PlanDto created = plansService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PLAN_UPDATE')")
    public ResponseEntity<PlanDto> update(@PathVariable UUID uuid,
                                          @Valid @RequestBody PlanUpdateRequest request) {
        return ResponseEntity.ok(plansService.update(uuid, request));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('PLAN_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(plansService.getUsage(uuid));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PLAN_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        plansService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }
}
