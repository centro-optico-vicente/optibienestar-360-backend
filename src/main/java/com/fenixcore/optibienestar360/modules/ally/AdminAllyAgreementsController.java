package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.service.AllyAgreementsService;
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
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/agreements} — admin
 * management of contracts (COMMERCIAL / MEDICAL / EXCLUSIVITY / SUPPLY)
 * between the platform and the ally.
 *
 * <p>Granular per V79: {@code ALLY_AGREEMENT_VIEW_ALL} / {@code _CREATE} /
 * {@code _UPDATE} / {@code _DELETE} — even reads stay off the standard
 * {@code ALLY_VIEW_ALL}, since agreements include commercial terms that
 * shouldn't be visible to every ally-viewer role.</p>
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/agreements")
@RequiredArgsConstructor
public class AdminAllyAgreementsController {

    private final AllyAgreementsService agreementsService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_AGREEMENT_VIEW_ALL')")
    public ResponseEntity<List<AllyAgreementDto>> list(@PathVariable UUID allyUuid) {
        return ResponseEntity.ok(agreementsService.listForAlly(allyUuid));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_AGREEMENT_VIEW_ALL')")
    public ResponseEntity<AllyAgreementDto> get(@PathVariable UUID allyUuid,
                                                @PathVariable UUID uuid) {
        return ResponseEntity.ok(agreementsService.get(allyUuid, uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ALLY_AGREEMENT_CREATE')")
    public ResponseEntity<AllyAgreementDto> create(@PathVariable UUID allyUuid,
                                                   @Valid @RequestBody AllyAgreementCreateRequest request) {
        AllyAgreementDto created = agreementsService.create(allyUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_AGREEMENT_UPDATE')")
    public ResponseEntity<AllyAgreementDto> update(@PathVariable UUID allyUuid,
                                                   @PathVariable UUID uuid,
                                                   @Valid @RequestBody AllyAgreementUpdateRequest request) {
        return ResponseEntity.ok(agreementsService.update(allyUuid, uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_AGREEMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID allyUuid,
                                       @PathVariable UUID uuid) {
        agreementsService.delete(allyUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
