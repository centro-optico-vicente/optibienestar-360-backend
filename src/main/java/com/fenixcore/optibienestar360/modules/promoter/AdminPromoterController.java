package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionPeriodSummaryDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDashboardDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionsService;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterDashboardService;
import com.fenixcore.optibienestar360.modules.promoter.service.PromotersService;
import com.fenixcore.optibienestar360.modules.catalog.dto.UsageDto;
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
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for promoters. 5 standard endpoints; the INSTITUCION system
 * row is visible via GET / list but mutations (PUT / DELETE) on it are
 * rejected with 422 — it's seed-only.
 */
@RestController
@RequestMapping("/v1/admin/promoters")
@RequiredArgsConstructor
public class AdminPromoterController {

    private final PromotersService promotersService;
    private final PromoterDashboardService promoterDashboardService;
    private final CommissionsService commissionsService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_ALL')")
    public ResponseEntity<Page<PromoterDto>> list(
            @PageableDefault(size = 50, sort = "displayName", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(promotersService.list(pageable, filter, q, includeInactive));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_ALL')")
    public ResponseEntity<PromoterDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(promotersService.get(uuid));
    }

    /** Portfolio + collection health + this-month commissions, admin view of {@code GET /v1/promoter/me}. */
    @GetMapping("/{uuid}/portfolio")
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_ALL')")
    public ResponseEntity<PromoterDashboardDto> portfolio(@PathVariable UUID uuid) {
        return ResponseEntity.ok(promoterDashboardService.getDashboardFor(uuid));
    }

    /** Monthly commission history for one promoter, most recent period first. */
    @GetMapping("/{uuid}/commissions/summary")
    @PreAuthorize("hasAuthority('COMMISSION_VIEW_ALL')")
    public ResponseEntity<List<CommissionPeriodSummaryDto>> commissionsSummary(@PathVariable UUID uuid) {
        return ResponseEntity.ok(commissionsService.periodSummaryFor(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROMOTER_CREATE')")
    public ResponseEntity<PromoterDto> create(@Valid @RequestBody PromoterCreateRequest request) {
        PromoterDto created = promotersService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_UPDATE')")
    public ResponseEntity<PromoterDto> update(@PathVariable UUID uuid,
                                              @Valid @RequestBody PromoterUpdateRequest request) {
        return ResponseEntity.ok(promotersService.update(uuid, request));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(promotersService.getUsage(uuid));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        promotersService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }
}
