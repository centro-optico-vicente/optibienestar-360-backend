package com.fenixcore.optibienestar360.modules.terms;

import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionCreateRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionDto;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionUpdateRequest;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.List;
import java.util.UUID;

/**
 * Admin CRUD for T&C versions (AFILIADO / PROMOTOR / ALIADO). No delete —
 * a version is either not-yet-vigente (editable, see {@code TermsVersionService.update})
 * or already vigente (immutable, permanent history).
 */
@RestController
@RequestMapping("/v1/admin/terms")
@RequiredArgsConstructor
public class AdminTermsController {

    private final TermsVersionService service;

    @GetMapping
    @PreAuthorize("hasAuthority('TERMS_VIEW_ALL')")
    public ResponseEntity<List<TermsVersionDto>> list(@RequestParam(required = false) TermType type) {
        return ResponseEntity.ok(type != null ? service.listByType(type) : service.listAll());
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('TERMS_VIEW_ALL')")
    public ResponseEntity<TermsVersionDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @GetMapping("/current/{type}")
    @PreAuthorize("hasAuthority('TERMS_VIEW_ALL')")
    public ResponseEntity<TermsVersionDto> current(@PathVariable TermType type) {
        return ResponseEntity.ok(service.getCurrent(type));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TERMS_CREATE')")
    public ResponseEntity<TermsVersionDto> create(@Valid @RequestBody TermsVersionCreateRequest request) {
        TermsVersionDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('TERMS_UPDATE')")
    public ResponseEntity<TermsVersionDto> update(@PathVariable UUID uuid,
                                                  @Valid @RequestBody TermsVersionUpdateRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }
}
