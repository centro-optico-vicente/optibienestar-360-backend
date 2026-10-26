package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.common.storage.dto.AttachedFileDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyProductsAdminService;
import com.fenixcore.optibienestar360.modules.product.entity.Product.ProductType;
import com.fenixcore.optibienestar360.modules.product.service.ProductImageService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ProductImageService imageService;

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

    // ─── Catalog image (spec .ai/specs/08-storage-r2.md §5) ────────────────
    // Transitional: the image belongs to the product since ADR 0026 and is
    // managed at /v1/admin/products/{uuid}/image. These endpoints act on the
    // image of the offer's product so the current frontend keeps working.

    /** Uploads (replacing any existing one) the product's image as a private attachment. */
    @PostMapping(value = "/{uuid}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT_IMAGE_UPLOAD')")
    public ResponseEntity<AttachedFileDto> uploadImage(
            @PathVariable UUID allyUuid,
            @PathVariable UUID uuid,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(imageService.upload(offersService.productUuidOf(allyUuid, uuid), image, actor.getUuid()));
    }

    /** Permanently publishes the uploaded image — copies it to the public bucket and sets {@code imageKey}. */
    @PostMapping("/{uuid}/image/publish")
    @PreAuthorize("hasAuthority('PRODUCT_IMAGE_UPLOAD')")
    public ResponseEntity<Void> publishImage(
            @PathVariable UUID allyUuid,
            @PathVariable UUID uuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        imageService.publish(offersService.productUuidOf(allyUuid, uuid), actor.getUuid());
        return ResponseEntity.noContent().build();
    }

    /** Retracts a published image (copies it back privately) — the file is kept, ready to be re-published. */
    @PostMapping("/{uuid}/image/unpublish")
    @PreAuthorize("hasAuthority('PRODUCT_IMAGE_UPLOAD')")
    public ResponseEntity<Void> unpublishImage(@PathVariable UUID allyUuid, @PathVariable UUID uuid) {
        imageService.unpublish(offersService.productUuidOf(allyUuid, uuid));
        return ResponseEntity.noContent().build();
    }

    /** Deletes the image outright — unlike {@link #unpublishImage}, this is definitive. */
    @DeleteMapping("/{uuid}/image")
    @PreAuthorize("hasAuthority('PRODUCT_IMAGE_DELETE')")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID allyUuid, @PathVariable UUID uuid) {
        imageService.delete(offersService.productUuidOf(allyUuid, uuid));
        return ResponseEntity.noContent().build();
    }
}
