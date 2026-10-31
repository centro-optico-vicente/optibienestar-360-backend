package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseVerifyRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyLicensesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/licenses} — professional
 * licenses of the ally's holder person (V213 / hub ADR 0031 §5). One
 * granular permission per action ({@code PERSON_LICENSE_*}).
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/licenses")
@RequiredArgsConstructor
public class AdminAllyLicensesController {

    private final AllyLicensesService licensesService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERSON_LICENSE_VIEW_ALL')")
    public ResponseEntity<List<AllyLicenseDto>> list(@PathVariable UUID allyUuid) {
        return ResponseEntity.ok(licensesService.list(allyUuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERSON_LICENSE_CREATE')")
    public ResponseEntity<AllyLicenseDto> add(@PathVariable UUID allyUuid,
                                              @Valid @RequestBody AllyLicenseRequest request) {
        AllyLicenseDto created = licensesService.add(allyUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PERSON_LICENSE_UPDATE')")
    public ResponseEntity<AllyLicenseDto> update(@PathVariable UUID allyUuid,
                                                 @PathVariable UUID uuid,
                                                 @Valid @RequestBody AllyLicenseRequest request) {
        return ResponseEntity.ok(licensesService.update(allyUuid, uuid, request));
    }

    @PostMapping("/{uuid}/verification")
    @PreAuthorize("hasAuthority('PERSON_LICENSE_VERIFY')")
    public ResponseEntity<AllyLicenseDto> verify(@PathVariable UUID allyUuid,
                                                 @PathVariable UUID uuid,
                                                 @Valid @RequestBody AllyLicenseVerifyRequest request,
                                                 @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(licensesService.verify(allyUuid, uuid, request, actor.getUuid()));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('PERSON_LICENSE_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID allyUuid, @PathVariable UUID uuid) {
        licensesService.delete(allyUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
