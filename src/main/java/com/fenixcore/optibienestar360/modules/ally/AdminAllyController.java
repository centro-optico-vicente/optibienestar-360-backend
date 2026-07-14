package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
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
 * Admin CRUD for the ally directory. Sub-resources (specialties / services /
 * agreements / users) get their own controllers per vertical-3 bullet
 * "/v1/admin/allies/{id}/specialties|services|agreements|users".
 *
 * <p>Pagination convention follows the project standard (see
 * {@code .ai/specs/06-rest-api.md}): default page size 20 + sort by
 * {@code name} ASC; client may override via {@code ?page=}, {@code ?size=},
 * {@code ?sort=}. Sentinel {@code size=-1} or {@code unpaged=true} returns
 * everything (resolved by {@code UnpagedAwarePageableArgumentResolver}).</p>
 */
@RestController
@RequestMapping("/v1/admin/allies")
@RequiredArgsConstructor
public class AdminAllyController {

    private final AlliesService alliesService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<Page<AllyListItemDto>> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(alliesService.list(pageable, filter, q));
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

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        alliesService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
