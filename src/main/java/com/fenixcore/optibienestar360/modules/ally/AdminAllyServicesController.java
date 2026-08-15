package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.common.storage.dto.AttachedFileDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyServiceImageService;
import com.fenixcore.optibienestar360.modules.ally.service.AllyServicesAdminService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/services} — admin CRUD for
 * the services an ally offers. Workflow transitions (approve / reject /
 * remove) are handled by separate endpoints under
 * {@code /v1/admin/ally-services/{uuid}/...} so the {@code ally_service_review_log}
 * captures the actor + reason of every status transition.
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/services")
@RequiredArgsConstructor
public class AdminAllyServicesController {

    private final AllyServicesAdminService servicesService;
    private final AllyServiceImageService imageService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<List<AllyServiceDto>> list(@PathVariable UUID allyUuid) {
        return ResponseEntity.ok(servicesService.listForAlly(allyUuid));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<AllyServiceDto> get(@PathVariable UUID allyUuid,
                                              @PathVariable UUID uuid) {
        return ResponseEntity.ok(servicesService.get(allyUuid, uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<AllyServiceDto> create(@PathVariable UUID allyUuid,
                                                 @Valid @RequestBody AllyServiceCreateRequest request) {
        AllyServiceDto created = servicesService.create(allyUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<AllyServiceDto> update(@PathVariable UUID allyUuid,
                                                 @PathVariable UUID uuid,
                                                 @Valid @RequestBody AllyServiceUpdateRequest request) {
        return ResponseEntity.ok(servicesService.update(allyUuid, uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable UUID allyUuid,
                                       @PathVariable UUID uuid) {
        servicesService.delete(allyUuid, uuid);
        return ResponseEntity.noContent().build();
    }

    // ─── Catalog image (spec .ai/specs/08-storage-r2.md §5) ────────────────
    // Independent authorities from ALLY_UPDATE — moderating the image is not
    // the same as editing price/name/description.

    /**
     * Uploads (replacing any existing one) the service's catalog image as a
     * private {@code INTERNAL} attachment — never directly public. Must be
     * published explicitly via {@link #publishImage} before it appears in
     * the public directory.
     */
    @PostMapping(value = "/{uuid}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ALLY_SERVICE_IMAGE_UPLOAD')")
    public ResponseEntity<AttachedFileDto> uploadImage(
            @PathVariable UUID allyUuid,
            @PathVariable UUID uuid,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(imageService.upload(allyUuid, uuid, image, actor.getUuid()));
    }

    /** Permanently publishes the uploaded image — copies it to the public bucket and sets {@code imageKey}. */
    @PostMapping("/{uuid}/image/publish")
    @PreAuthorize("hasAuthority('ALLY_SERVICE_IMAGE_UPLOAD')")
    public ResponseEntity<Void> publishImage(
            @PathVariable UUID allyUuid,
            @PathVariable UUID uuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        imageService.publish(allyUuid, uuid, actor.getUuid());
        return ResponseEntity.noContent().build();
    }

    /** Retracts a published image (copies it back privately) — the file is kept, ready to be re-published. */
    @PostMapping("/{uuid}/image/unpublish")
    @PreAuthorize("hasAuthority('ALLY_SERVICE_IMAGE_UPLOAD')")
    public ResponseEntity<Void> unpublishImage(@PathVariable UUID allyUuid, @PathVariable UUID uuid) {
        imageService.unpublish(allyUuid, uuid);
        return ResponseEntity.noContent().build();
    }

    /** Deletes the image outright — unlike {@link #unpublishImage}, this is definitive. */
    @DeleteMapping("/{uuid}/image")
    @PreAuthorize("hasAuthority('ALLY_SERVICE_IMAGE_DELETE')")
    public ResponseEntity<Void> deleteImage(@PathVariable UUID allyUuid, @PathVariable UUID uuid) {
        imageService.delete(allyUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
