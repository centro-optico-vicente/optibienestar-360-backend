package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.core.config.PublicHttpCacheConfig;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicProductListItemDto;
import com.fenixcore.optibienestar360.modules.ally.service.PublicProductSearch;
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
 * Anonymous-facing <b>cross-ally</b> product and service catalog (hub ADR
 * 0026) — consumed mainly by the public landing and open to external
 * clients. Mounted under {@code /v1/public/**} which the security config
 * ({@code SecurityConfig.PUBLIC_PATHS}) routes through {@code permitAll()}.
 *
 * <p>Only offers cleared for public view are returned (offer approved +
 * published + active, active product, parent ally active + published). The
 * list is built for leads and visitors, so every filter takes plain words —
 * accent- and case-insensitive, all optional, combined with AND:</p>
 * <ul>
 *   <li>{@code q} — anything: product, category, ally, city or state name;</li>
 *   <li>{@code type} — {@code servicio} / {@code producto};</li>
 *   <li>{@code category} — category name or code;</li>
 *   <li>{@code city} — the ally's city or state;</li>
 *   <li>{@code ally} — the ally's name, RIF ({@code J-12345678-9}) or ally number.</li>
 * </ul>
 * <p>{@code categoryUuid} / {@code cityUuid} / {@code allyUuid} remain for
 * clients that already hold ids. Default order is product name ascending.
 * The detail shows one product with every ally that offers it and its own
 * price and discount.</p>
 */
@RestController
// /v1/public/services is a deprecated alias kept while the landing moves to /products.
@RequestMapping({"/v1/public/products", "/v1/public/services"})
@RequiredArgsConstructor
public class PublicProductController {

    private final PublicProductsService publicProductsService;

    @GetMapping
    public ResponseEntity<Page<PublicProductListItemDto>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID categoryUuid,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) UUID cityUuid,
            @RequestParam(required = false) String ally,
            @RequestParam(required = false) UUID allyUuid,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        PublicProductSearch search = new PublicProductSearch(q, type, category, categoryUuid, city, cityUuid, ally, allyUuid);
        return ResponseEntity.ok().cacheControl(PublicHttpCacheConfig.publicCatalog())
                .body(publicProductsService.search(search, pageable));
    }

    /**
     * One product with the allies that offer it, best final price first.
     * Optional {@code city} (city or state name) or {@code cityUuid} keeps the
     * allies of one place. 404 when the product doesn't exist, is inactive or
     * nobody offers it publicly.
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<PublicProductDetailDto> detail(@PathVariable UUID uuid,
                                                         @RequestParam(required = false) String city,
                                                         @RequestParam(required = false) UUID cityUuid) {
        return ResponseEntity.ok().cacheControl(PublicHttpCacheConfig.publicCatalog())
                .body(publicProductsService.productDetail(uuid, cityUuid, city));
    }
}
