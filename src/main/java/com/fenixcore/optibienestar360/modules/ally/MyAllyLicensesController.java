package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyLicenseRequest;
import com.fenixcore.optibienestar360.modules.ally.service.MyAllyLicensesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The professional ally's own licenses (hub ADR 0036 §5). Reading needs
 * {@code ALLY_VIEW_OWN}; adding or removing {@code ALLY_LICENSE_UPDATE_OWN}
 * (roles ALIADO and ALIADO_PENDIENTE). Membership based, like the rest of
 * {@code /v1/me/allies}. Verification stays a staff action.
 */
@RestController
@RequestMapping({"/v1/me/allies/{allyUuid}/licenses", "/v1/allies/me/{allyUuid}/licenses"})
@RequiredArgsConstructor
public class MyAllyLicensesController {

    private final MyAllyLicensesService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_OWN')")
    public ResponseEntity<List<AllyLicenseDto>> list(@PathVariable UUID allyUuid,
                                                     @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.list(allyUuid, actor.getUuid()));
    }

    @GetMapping("/bodies")
    @PreAuthorize("hasAuthority('ALLY_VIEW_OWN')")
    public ResponseEntity<List<DisplayRef>> bodies(@PathVariable UUID allyUuid,
                                                   @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.bodies(allyUuid, actor.getUuid()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ALLY_LICENSE_UPDATE_OWN')")
    public ResponseEntity<AllyLicenseDto> add(@PathVariable UUID allyUuid,
                                              @Valid @RequestBody AllyLicenseRequest request,
                                              @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(allyUuid, actor.getUuid(), request));
    }

    @DeleteMapping("/{licenseUuid}")
    @PreAuthorize("hasAuthority('ALLY_LICENSE_UPDATE_OWN')")
    public ResponseEntity<Void> delete(@PathVariable UUID allyUuid, @PathVariable UUID licenseUuid,
                                       @AuthenticationPrincipal CustomUserDetails actor) {
        service.delete(allyUuid, licenseUuid, actor.getUuid());
        return ResponseEntity.noContent().build();
    }
}
