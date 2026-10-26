package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyProductsAdminService;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/products} — admin CRUD for
 * what an ally offers (hub ADR 0026): each offer points at a general product
 * or one exclusive to the ally, with the ally's own price, discount and
 * exclusions. Workflow transitions (approve / reject / remove) are handled by
 * separate endpoints under {@code /v1/admin/ally-products/{uuid}/...} so the
 * {@code ally_product_review_log} captures the actor + reason of every transition.
 */
@RestController
// .../services is a deprecated alias kept while the frontend moves to .../products (ADR 0026).
@RequestMapping({"/v1/admin/allies/{allyUuid}/products", "/v1/admin/allies/{allyUuid}/services"})
@RequiredArgsConstructor
public class AdminAllyProductsController {

    private final AllyProductsAdminService offersService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_VIEW_ALL')")
    public ResponseEntity<Page<AllyProductDto>> list(@PathVariable UUID allyUuid,
                                                     @RequestParam(required = false) ProductType type,
                                                     @RequestParam(required = false) String q,
                                                     Pageable pageable) {
        return ResponseEntity.ok(offersService.listForAlly(allyUuid, type, q, pageable));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_VIEW_ALL')")
    public ResponseEntity<AllyProductDto> get(@PathVariable UUID allyUuid,
                                              @PathVariable UUID uuid) {
        return ResponseEntity.ok(offersService.get(allyUuid, uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_CREATE')")
    public ResponseEntity<AllyProductDto> create(@PathVariable UUID allyUuid,
                                                 @Valid @RequestBody AllyProductCreateRequest request) {
        AllyProductDto created = offersService.create(allyUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_UPDATE')")
    public ResponseEntity<AllyProductDto> update(@PathVariable UUID allyUuid,
                                                 @PathVariable UUID uuid,
                                                 @Valid @RequestBody AllyProductUpdateRequest request) {
        return ResponseEntity.ok(offersService.update(allyUuid, uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_PRODUCT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID allyUuid,
                                       @PathVariable UUID uuid) {
        offersService.delete(allyUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
