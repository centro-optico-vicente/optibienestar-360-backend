package com.fenixcore.optibienestar360.modules.product;

import com.fenixcore.optibienestar360.common.storage.dto.AttachedFileDto;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.product.dto.ProductCreateRequest;
import com.fenixcore.optibienestar360.modules.product.dto.ProductDto;
import com.fenixcore.optibienestar360.modules.product.dto.ProductListItemDto;
import com.fenixcore.optibienestar360.modules.product.dto.ProductUpdateRequest;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import com.fenixcore.optibienestar360.modules.product.service.ProductImageService;
import com.fenixcore.optibienestar360.modules.product.service.ProductsService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Admin CRUD for the product master (hub ADR 0026) — general products any
 * ally may offer, and exclusive ones owned by a single ally. What each ally
 * offers (price, discount, review) lives under
 * {@code /v1/admin/allies/{allyUuid}/products}.
 *
 * <p>No default sort on purpose — an unsorted {@code Pageable} falls back to
 * {@code entity_config}'s configured default (see {@code AdminAllyController}).</p>
 */
@RestController
@RequestMapping("/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductsController {

    private final ProductsService productsService;
    private final ProductImageService imageService;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<ProductListItemDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductType type,
            @RequestParam(required = false) Boolean exclusive,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        Page<ProductListItemDto> page = productsService.list(pageable, filter, q, type, exclusive, includeInactive);
        return ResponseEntity.ok(new AppliedSortPage<>(page, productsService.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW_ALL')")
    public ResponseEntity<ProductDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(productsService.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductDto created = productsService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public ResponseEntity<ProductDto> update(@PathVariable UUID uuid,
                                             @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(productsService.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        productsService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    // ─── Catalog image (spec .ai/specs/08-storage-r2.md §5) ────────────────
    // Independent authorities from PRODUCT_UPDATE — moderating the image is
    // not the same as editing the product's data.

    /** Uploads (replacing any existing one) as a private attachment; must be published explicitly. */
    @PostMapping(value = "/{uuid}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT_IMAGE_UPLOAD')")
    public ResponseEntity<AttachedFileDto> uploadImage(@PathVariable UUID uuid,
                                                       @RequestPart("image") MultipartFile image,
                                                       @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(imageService.upload(uuid, image, actor.getUuid()));
    }

    @PostMapping("/{uuid}/image/publish")
    @PreAuthorize("hasAuthority('PRODUCT_IMAGE_UPLOAD')")
    public ResponseEntity<Void> publishImage(@PathVariable UUID uuid,
                                             @AuthenticationPrincipal CustomUserDetails actor) {
        imageService.publish(uuid, actor.getUuid());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uuid}/image/unpublish")
    @PreAuthorize("hasAuthority('PRODUCT_IMAGE_UPLOAD')")
    public ResponseEntity<Void> unpublishImage(@PathVariable UUID uuid) {
        imageService.unpublish(uuid);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{uuid}/image")
    @PreAuthorize("hasAuthority('PRODUCT_IMAGE_DELETE')")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID uuid) {
        imageService.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
