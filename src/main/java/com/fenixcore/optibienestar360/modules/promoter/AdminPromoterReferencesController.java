package com.fenixcore.optibienestar360.modules.promoter;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterReferenceCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterReferenceDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterReferenceUpdateRequest;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterReferencesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/promoters/{promoterUuid}/references} —
 * advisor personal references (V180, dossier section IV).
 *
 * <p>Auth reuses {@code PROMOTER_*} permissions (no dedicated
 * {@code PROMOTER_REFERENCE_*} set — managing the dossier is part of
 * managing the promoter, same as beneficiaries ride on {@code MEMBER_*}).</p>
 */
@RestController
@RequestMapping("/v1/admin/promoters/{promoterUuid}/references")
@RequiredArgsConstructor
public class AdminPromoterReferencesController {

    private final PromoterReferencesService referencesService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_ALL')")
    public ResponseEntity<List<PromoterReferenceDto>> list(@PathVariable UUID promoterUuid) {
        return ResponseEntity.ok(referencesService.listForPromoter(promoterUuid));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_VIEW_ALL')")
    public ResponseEntity<PromoterReferenceDto> get(@PathVariable UUID promoterUuid, @PathVariable UUID uuid) {
        return ResponseEntity.ok(referencesService.get(promoterUuid, uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROMOTER_UPDATE')")
    public ResponseEntity<PromoterReferenceDto> add(@PathVariable UUID promoterUuid,
                                                    @Valid @RequestBody PromoterReferenceCreateRequest request) {
        PromoterReferenceDto created = referencesService.add(promoterUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_UPDATE')")
    public ResponseEntity<PromoterReferenceDto> update(@PathVariable UUID promoterUuid,
                                                       @PathVariable UUID uuid,
                                                       @Valid @RequestBody PromoterReferenceUpdateRequest request) {
        return ResponseEntity.ok(referencesService.update(promoterUuid, uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PROMOTER_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable UUID promoterUuid, @PathVariable UUID uuid) {
        referencesService.delete(promoterUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
