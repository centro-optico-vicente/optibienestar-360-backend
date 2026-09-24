package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.catalog.dto.AllyTypeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/ally-types}.
 *
 * <p>Single-item add / remove against the {@code @ManyToMany} pivot
 * {@code ally_ally_types} (V157 — ally types are now multi-valued, e.g. a
 * Farmacia expanding into Laboratorio). For bulk-replace operations, use the
 * parent's {@code PUT /v1/admin/allies/{uuid}} with {@code allyTypeUuids}.
 * Named to avoid clashing with the existing catalog CRUD
 * {@code AllyTypeController} (which manages {@code ally_types} rows
 * themselves, not their relation to a given ally).</p>
 *
 * <p>{@code remove} rejects (422, {@code ally_type.min_required}) when it
 * would leave the ally with zero types.</p>
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/ally-types")
@RequiredArgsConstructor
public class AdminAllyTypeRelationController {

    private final AlliesService alliesService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<List<AllyTypeDto>> list(@PathVariable UUID allyUuid, Pageable pageable) {
        return ResponseEntity.ok(alliesService.listAllyTypes(allyUuid, pageable));
    }

    @PostMapping("/{typeUuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<Void> add(@PathVariable UUID allyUuid,
                                    @PathVariable UUID typeUuid) {
        alliesService.addAllyType(allyUuid, typeUuid);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{typeUuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<Void> remove(@PathVariable UUID allyUuid,
                                       @PathVariable UUID typeUuid) {
        alliesService.removeAllyType(allyUuid, typeUuid);
        return ResponseEntity.noContent().build();
    }
}
