package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyServicesAdminService;
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
}
