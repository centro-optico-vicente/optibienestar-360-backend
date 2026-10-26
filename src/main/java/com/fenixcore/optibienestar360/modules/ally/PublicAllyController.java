package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.core.config.PublicHttpCacheConfig;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyListItemDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.ally.service.PublicAllySearch;
import com.fenixcore.optibienestar360.modules.ally.service.PublicProductsService;
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
 * <p>Filtering takes plain words, as a lead or visitor would type them
 * (accent- and case-insensitive, all optional, combined with AND): {@code q}
 * over the ally's name, RIF, ally number, description, city and state;
 * {@code city} (city or state name); {@code category} / {@code type} — allies
 * publicly offering something of that category or of type
 * {@code servicio}/{@code producto} (hub ADR 0026). The id twins
 * ({@code cityUuid}, {@code professionUuid}, {@code categoryUuid}) remain for
 * clients that hold ids.</p>
 */
@RestController
@RequestMapping("/v1/public/allies")
@RequiredArgsConstructor
public class PublicAllyController {

    private final AlliesService alliesService;
    private final PublicProductsService publicProductsService;

    @GetMapping
    public ResponseEntity<Page<PublicAllyListItemDto>> directory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) UUID cityUuid,
            @RequestParam(required = false) UUID professionUuid,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID categoryUuid,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        PublicAllySearch search = new PublicAllySearch(q, city, cityUuid, professionUuid, category, categoryUuid, type);
        return ResponseEntity.ok().cacheControl(PublicHttpCacheConfig.publicCatalog())
                .body(alliesService.publicDirectory(search, pageable));
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
        return ResponseEntity.ok().cacheControl(PublicHttpCacheConfig.publicCatalog())
                .body(alliesService.publicGetByUuid(uuid));
    }

    /**
     * One ally's public products and services as a dedicated, paginated,
     * filterable list — the same data the detail nests, but standalone. 404s
     * when the ally doesn't exist or is not publicly visible. Offers are
     * pre-filtered to active + published + APPROVED over an active product.
     * Optional filters: {@code category} (name or code) / {@code categoryUuid},
     * {@code type} ({@code servicio}/{@code producto}), free-text {@code q}.
     * {@code /services} is a deprecated alias kept while the landing moves over.
     */
    @GetMapping({"/{uuid}/products", "/{uuid}/services"})
    public ResponseEntity<Page<PublicAllyProductDto>> products(
            @PathVariable UUID uuid,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID categoryUuid,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok().cacheControl(PublicHttpCacheConfig.publicCatalog())
                .body(publicProductsService.listByAlly(uuid, categoryUuid, category, type, q, pageable));
    }
}
