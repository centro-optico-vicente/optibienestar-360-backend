package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
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
import java.util.UUID;

/**
 * Admin CRUD for the ally directory. Sub-resources (professions / services /
 * agreements / users) get their own controllers per vertical-3 bullet
 * "/v1/admin/allies/{id}/professions|services|agreements|users".
 *
 * <p>Pagination convention follows the project standard (see
 * {@code .ai/specs/06-rest-api.md}): default page size 20; client may
 * override via {@code ?page=}, {@code ?size=}, {@code ?sort=}. Sentinel
 * {@code size=-1} or {@code unpaged=true} returns everything (resolved by
 * {@code UnpagedAwarePageableArgumentResolver}). No default sort here on
 * purpose — an unsorted {@code Pageable} lets {@link AlliesService} tell a
 * client-requested sort apart from "none given" and fall back to
 * {@code entity_config}'s configured default (or {@code createdAt DESC} if
 * unconfigured).</p>
 */
@RestController
@RequestMapping("/v1/admin/allies")
@RequiredArgsConstructor
public class AdminAllyController {

    private final AlliesService alliesService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
	public ResponseEntity<AppliedSortPage<AllyListItemDto>> list(
			@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
		// Reports back which columns/directions an unsorted request actually landed
		// on (entity_config → system_configs → createdAt DESC) — the table has no
		// other way to reflect that default in its header arrows.
		Page<AllyListItemDto> page = alliesService.list(pageable, filter, q, includeInactive);
		return ResponseEntity.ok(new AppliedSortPage<>(page, alliesService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<AllyDetailDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(alliesService.getDetail(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ALLY_CREATE')")
    public ResponseEntity<AllyDetailDto> create(@Valid @RequestBody AllyCreateRequest request) {
        AllyDetailDto created = alliesService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<AllyDetailDto> update(@PathVariable UUID uuid,
                                                @Valid @RequestBody AllyUpdateRequest request) {
        return ResponseEntity.ok(alliesService.update(uuid, request));
    }

    @GetMapping("/{uuid}/usage")
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<UsageDto> usage(@PathVariable UUID uuid) {
        return ResponseEntity.ok(alliesService.getUsage(uuid));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
            @RequestParam(defaultValue = "false") boolean physical) {
        alliesService.delete(uuid, physical);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/restore")
    @PreAuthorize("hasAuthority('ALLY_DELETE')")
    public ResponseEntity<AllyDetailDto> restore(@PathVariable UUID uuid) {
        return ResponseEntity.ok(alliesService.restore(uuid));
    }

}
