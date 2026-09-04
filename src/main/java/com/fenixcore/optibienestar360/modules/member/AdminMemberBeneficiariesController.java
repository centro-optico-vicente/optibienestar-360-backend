package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryCreateRequest;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryDto;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryUpdateRequest;
import com.fenixcore.optibienestar360.modules.member.service.BeneficiariesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
 * Sub-resource {@code /v1/admin/members/{memberUuid}/beneficiaries} — admin
 * management of the titular's covered family members.
 *
 * <p>Auth reuses {@code MEMBER_*} permissions (no dedicated
 * {@code BENEFICIARY_*} permissions exist — managing the family grid is
 * part of managing the member).</p>
 */
@RestController
@RequestMapping("/v1/admin/members/{memberUuid}/beneficiaries")
@RequiredArgsConstructor
public class AdminMemberBeneficiariesController {

    private final BeneficiariesService beneficiariesService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_ALL')")
    public ResponseEntity<List<BeneficiaryDto>> list(@PathVariable UUID memberUuid, Pageable pageable) {
        return ResponseEntity.ok(beneficiariesService.listForMember(memberUuid, pageable));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_VIEW_ALL')")
    public ResponseEntity<BeneficiaryDto> get(@PathVariable UUID memberUuid,
                                              @PathVariable UUID uuid) {
        return ResponseEntity.ok(beneficiariesService.get(memberUuid, uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MEMBER_UPDATE')")
    public ResponseEntity<BeneficiaryDto> add(@PathVariable UUID memberUuid,
                                              @Valid @RequestBody BeneficiaryCreateRequest request) {
        BeneficiaryDto created = beneficiariesService.add(memberUuid, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_UPDATE')")
    public ResponseEntity<BeneficiaryDto> update(@PathVariable UUID memberUuid,
                                                 @PathVariable UUID uuid,
                                                 @Valid @RequestBody BeneficiaryUpdateRequest request) {
        return ResponseEntity.ok(beneficiariesService.update(memberUuid, uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_UPDATE')")
    public ResponseEntity<Void> delete(@PathVariable UUID memberUuid,
                                       @PathVariable UUID uuid) {
        beneficiariesService.delete(memberUuid, uuid);
        return ResponseEntity.noContent().build();
    }
}
