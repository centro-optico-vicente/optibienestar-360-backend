package com.fenixcore.optisaludplus.modules.ally;

import com.fenixcore.optisaludplus.modules.ally.service.AlliesService;
import com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyDto;
import lombok.RequiredArgsConstructor;
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
 * Sub-resource {@code /v1/admin/allies/{allyUuid}/specialties}.
 *
 * <p>Single-item add / remove against the {@code @ManyToMany} pivot
 * {@code ally_specialties}. For bulk-replace operations, use the parent's
 * {@code PUT /v1/admin/allies/{uuid}} with {@code specialtyUuids}.</p>
 */
@RestController
@RequestMapping("/v1/admin/allies/{allyUuid}/specialties")
@RequiredArgsConstructor
public class AdminAllySpecialtiesController {

    private final AlliesService alliesService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_ALL')")
    public ResponseEntity<List<MedicalSpecialtyDto>> list(@PathVariable UUID allyUuid) {
        return ResponseEntity.ok(alliesService.listSpecialties(allyUuid));
    }

    @PostMapping("/{specialtyUuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<Void> add(@PathVariable UUID allyUuid,
                                    @PathVariable UUID specialtyUuid) {
        alliesService.addSpecialty(allyUuid, specialtyUuid);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{specialtyUuid}")
    @PreAuthorize("hasAuthority('ALLY_UPDATE')")
    public ResponseEntity<Void> remove(@PathVariable UUID allyUuid,
                                       @PathVariable UUID specialtyUuid) {
        alliesService.removeSpecialty(allyUuid, specialtyUuid);
        return ResponseEntity.noContent().build();
    }
}
