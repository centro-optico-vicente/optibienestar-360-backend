package com.fenixcore.optisaludplus.modules.ally;

import com.fenixcore.optisaludplus.modules.ally.dto.PublicAllyDetailDto;
import com.fenixcore.optisaludplus.modules.ally.dto.PublicAllyListItemDto;
import com.fenixcore.optisaludplus.modules.ally.service.AlliesService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Anonymous-facing directory of allies. Mounted under {@code /v1/public/**}
 * which the security config ({@code SecurityConfig.PUBLIC_PATHS}) already
 * routes through {@code permitAll()}, so no JWT is required.
 *
 * <p>Returns the sanitized {@link PublicAllyListItemDto} — internal fields
 * (RIF, audit, status, email, manager, publishedAt, sub-collections) are
 * intentionally omitted; see that DTO's javadoc for the rationale.</p>
 *
 * <p>Filtering: by city UUID, by medical specialty UUID, by free-text
 * {@code q} (accent-insensitive over {@code name + description}). All
 * optional; combined with AND.</p>
 */
@RestController
@RequestMapping("/v1/public/allies")
@RequiredArgsConstructor
public class PublicAllyController {

    private final AlliesService alliesService;

    @GetMapping
    public ResponseEntity<Page<PublicAllyListItemDto>> directory(
            @RequestParam(required = false) UUID cityUuid,
            @RequestParam(required = false) UUID specialtyUuid,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(alliesService.publicDirectory(cityUuid, specialtyUuid, q, pageable));
    }

    /**
     * Detail page for a single ally. 404s when the ally doesn't exist OR is
     * not publicly visible (inactive / unpublished) — anonymous callers
     * never learn an ally exists if it shouldn't be visible. Services in the
     * response are pre-filtered to only those approved + published + active;
     * everything else is invisible at this layer.
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<PublicAllyDetailDto> detail(@PathVariable UUID uuid) {
        return ResponseEntity.ok(alliesService.publicGetByUuid(uuid));
    }
}
