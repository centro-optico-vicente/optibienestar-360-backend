package com.fenixcore.optibienestar360.modules.corporate;

import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateBulkEnrollResponse;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractCreateRequest;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractDto;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateContractUpdateRequest;
import com.fenixcore.optibienestar360.modules.corporate.dto.CorporateMemberBulkRequest;
import com.fenixcore.optibienestar360.modules.corporate.service.CorporateContractsService;
import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.member.dto.MemberListItemDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.UUID;

/**
 * Admin CRUD for corporate contracts (v2 PDF — "Contratos Corporativos", V38)
 * plus bulk member enrollment and the per-contract member listing.
 *
 * <p>Contract CRUD is granular per V79: {@code CORPORATE_CONTRACT_VIEW_ALL}
 * (untouched since V38) / {@code _CREATE} / {@code _UPDATE} / {@code _DELETE}.
 * The contract↔member association (bulk-enroll, member listing) is a separate
 * concern with its own CRUD set — {@code CORPORATE_CONTRACT_MEMBER_VIEW_ALL} /
 * {@code _CREATE} / {@code _UPDATE} / {@code _DELETE} — since enrolling
 * members isn't editing the contract's own fields. Pagination follows the
 * project convention: page size 20, {@code createdAt} DESC (newest first).</p>
 */
@RestController
@RequestMapping("/v1/admin/corporate-contracts")
@RequiredArgsConstructor
public class AdminCorporateContractController {

    private final CorporateContractsService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CORPORATE_CONTRACT_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<CorporateContractDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String q) {
        Page<CorporateContractDto> page = service.list(pageable, filter, q);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CORPORATE_CONTRACT_VIEW_ALL')")
    public ResponseEntity<CorporateContractDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CORPORATE_CONTRACT_CREATE')")
    public ResponseEntity<CorporateContractDto> create(
            @Valid @RequestBody CorporateContractCreateRequest request) {
        CorporateContractDto created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CORPORATE_CONTRACT_UPDATE')")
    public ResponseEntity<CorporateContractDto> update(@PathVariable UUID uuid,
            @Valid @RequestBody CorporateContractUpdateRequest request) {
        return ResponseEntity.ok(service.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('CORPORATE_CONTRACT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    /** Bulk-enroll members under the contract (CSV/list). Returns a per-row outcome. */
    @PostMapping("/{uuid}/members")
    @PreAuthorize("hasAuthority('CORPORATE_CONTRACT_MEMBER_CREATE')")
    public ResponseEntity<CorporateBulkEnrollResponse> enrollMembers(@PathVariable UUID uuid,
            @Valid @RequestBody CorporateMemberBulkRequest request) {
        return ResponseEntity.ok(service.enrollMembers(uuid, request));
    }

    @GetMapping("/{uuid}/members")
    @PreAuthorize("hasAuthority('CORPORATE_CONTRACT_MEMBER_VIEW_ALL')")
    public ResponseEntity<Page<MemberListItemDto>> listMembers(@PathVariable UUID uuid,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.listMembers(uuid, pageable));
    }
}
