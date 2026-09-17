package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterRankCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterRankDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterRankReorderRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterRankUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterRankService;
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
 * Admin CRUD over {@code promoter_ranks} (V101, the "cargo" catalog). Same
 * 4-permission-per-catalog convention as {@code AdminCatalogsController} —
 * {@code PROMOTER_RANK_VIEW_ALL}/{@code _CREATE}/{@code _UPDATE}/{@code
 * _DELETE} — kept as its own controller (not folded into {@code
 * AdminCatalogsController}) since it lives in the promoter module, not
 * {@code modules.catalog}.
 */
@RestController
@RequestMapping("/v1/admin/promoter-ranks")
@RequiredArgsConstructor
public class AdminPromoterRankController {

    private static final String VIEW = "hasAuthority('PROMOTER_RANK_VIEW_ALL')";
    private static final String CREATE = "hasAuthority('PROMOTER_RANK_CREATE')";
    private static final String UPDATE = "hasAuthority('PROMOTER_RANK_UPDATE')";
    private static final String DELETE = "hasAuthority('PROMOTER_RANK_DELETE')";
    private static final String REORDER = "hasAuthority('PROMOTER_RANK_REORDER')";

    private final PromoterRankService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public ResponseEntity<AppliedSortPage<PromoterRankDto>> list(
            @PageableDefault(size = 50) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<PromoterRankDto> page = service.list(pageable, filter, q, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    /**
     * @param excludeUuid optional — the change-rank flow's "pick the new
     *                     rank" step passes the promoter's current rank here
     *                     so it doesn't appear as an option (changing to the
     *                     same rank isn't a change).
     */
    @GetMapping("/options")
    @PreAuthorize(VIEW)
    public ResponseEntity<List<OptionDto>> options(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<UUID> currentValues,
            @RequestParam(required = false) UUID excludeUuid) {
        return ResponseEntity.ok(service.listOptions(q, limit, currentValues, excludeUuid));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize(VIEW)
    public ResponseEntity<PromoterRankDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize(CREATE)
    public ResponseEntity<PromoterRankDto> create(@Valid @RequestBody PromoterRankCreateRequest req) {
        PromoterRankDto created = service.create(req);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}").buildAndExpand(created.uuid()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize(UPDATE)
    public ResponseEntity<PromoterRankDto> update(@PathVariable UUID uuid,
                                                   @Valid @RequestBody PromoterRankUpdateRequest req) {
        return ResponseEntity.ok(service.update(uuid, req));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize(DELETE)
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Moves {@code uuid} to the position immediately after {@code
     * req.afterRankUuid()} (or the beginning, if {@code null}) in the
     * hierarchy (V111). Returns the full, freshly-ordered list of active
     * ranks so the frontend can redraw without a follow-up {@code list} call.
     */
    @PutMapping("/{uuid}/reorder")
    @PreAuthorize(REORDER)
    public ResponseEntity<List<PromoterRankDto>> reorder(@PathVariable UUID uuid,
                                                          @Valid @RequestBody PromoterRankReorderRequest req) {
        return ResponseEntity.ok(service.reorder(uuid, req));
    }
}
