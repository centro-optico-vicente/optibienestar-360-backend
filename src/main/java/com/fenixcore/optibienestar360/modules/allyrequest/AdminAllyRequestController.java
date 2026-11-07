package com.fenixcore.optibienestar360.modules.allyrequest;

import com.fenixcore.optibienestar360.core.util.AppliedSortPage;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AgreementSupportUrlDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyImportResultDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestApproveRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestDetailDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestInviteRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestListItemDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestRejectRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.importer.AllyImportService;
import com.fenixcore.optibienestar360.modules.allyrequest.importer.AllyImportTemplate;
import com.fenixcore.optibienestar360.modules.allyrequest.service.AllyRequestService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Review of the ally requests (hub ADR 0036) and the bulk import of allies
 * from the template (Excel, or CSV separated by semicolon, comma or tab).
 * One granular permission per action: {@code ALLY_REGISTRATION_VIEW_ALL}
 * (list, count, detail, support link), {@code _APPROVE}, {@code _REJECT}
 * (reject and reopen), {@code _INVITE}, and {@code ALLY_IMPORT}.
 */
@RestController
@RequestMapping("/v1/admin/ally-requests")
@RequiredArgsConstructor
public class AdminAllyRequestController {

    private final AllyRequestService service;
    private final AllyImportService importService;

    /** {@code ?status=PENDING|APPROVED|REJECTED}, {@code ?channel=SELF_SERVICE|IMPORT}, {@code ?q=}; newest first by default. */
    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_VIEW_ALL')")
    public ResponseEntity<AppliedSortPage<AllyRequestListItemDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String q) {
        Page<AllyRequestListItemDto> page = service.list(pageable, status, channel, q);
        return ResponseEntity.ok(new AppliedSortPage<>(page, service.effectiveSort(pageable)));
    }

    /** Badge of the menu entry: {@code {"count": n}}. */
    @GetMapping("/count")
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_VIEW_ALL')")
    public ResponseEntity<Map<String, Long>> count(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(Map.of("count", service.count(status)));
    }

    @GetMapping("/{uuid}")
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_VIEW_ALL')")
    public ResponseEntity<AllyRequestDetailDto> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(service.get(uuid));
    }

    @PostMapping(value = "/{uuid}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_APPROVE')")
    public ResponseEntity<AllyRequestDetailDto> approve(@PathVariable UUID uuid,
                                                        @Valid @RequestBody(required = false) AllyRequestApproveRequest request,
                                                        @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.approve(uuid, request, null, actor.getUuid()));
    }

    /** Same as the JSON variant plus an optional {@code support} part: the signed agreement. */
    @PostMapping(value = "/{uuid}/approve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_APPROVE')")
    public ResponseEntity<AllyRequestDetailDto> approveMultipart(
            @PathVariable UUID uuid,
            @Valid @RequestPart(value = "request", required = false) AllyRequestApproveRequest request,
            @RequestPart(value = "support", required = false) MultipartFile support,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.approve(uuid, request, support, actor.getUuid()));
    }

    @PostMapping("/{uuid}/reject")
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_REJECT')")
    public ResponseEntity<AllyRequestDetailDto> reject(@PathVariable UUID uuid,
                                                       @Valid @RequestBody AllyRequestRejectRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.reject(uuid, request.reason(), actor.getUuid()));
    }

    @PostMapping("/{uuid}/reopen")
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_REJECT')")
    public ResponseEntity<AllyRequestDetailDto> reopen(@PathVariable UUID uuid,
                                                       @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.reopen(uuid, actor.getUuid()));
    }

    @PostMapping("/{uuid}/invite")
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_INVITE')")
    public ResponseEntity<AllyRequestDetailDto> invite(@PathVariable UUID uuid,
                                                       @Valid @RequestBody(required = false) AllyRequestInviteRequest request,
                                                       @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(service.invite(uuid, request, actor.getUuid()));
    }

    @GetMapping("/{uuid}/agreements/{agreementUuid}/support-url")
    @PreAuthorize("hasAuthority('ALLY_REGISTRATION_VIEW_ALL')")
    public ResponseEntity<AgreementSupportUrlDto> supportUrl(@PathVariable UUID uuid, @PathVariable UUID agreementUuid) {
        return ResponseEntity.ok(service.supportUrl(uuid, agreementUuid));
    }

    // ─── Import ─────────────────────────────────────────────────────────────

    /**
     * Imports allies from the template: {@code .xlsx}, or a text file
     * ({@code .csv}, {@code .tsv}, {@code .txt}) separated by semicolon, comma
     * or tab — {@code delimiter=auto} (default) detects it from the header row.
     * {@code dryRun=true} (default) only validates; {@code false} creates every
     * row in one transaction, or none if any row fails.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ALLY_IMPORT')")
    public ResponseEntity<AllyImportResultDto> importAllies(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "auto") String delimiter) {
        return ResponseEntity.ok(importService.importFile(file, dryRun, delimiter));
    }

    /** The empty template: {@code format=xlsx} (default) or {@code csv} (semicolon, UTF-8 with BOM). */
    @GetMapping("/import/template")
    @PreAuthorize("hasAuthority('ALLY_IMPORT')")
    public ResponseEntity<byte[]> template(@RequestParam(defaultValue = "xlsx") String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv ? AllyImportTemplate.csv() : AllyImportTemplate.xlsx();
        String filename = csv ? "plantilla-registro-aliados.csv" : "plantilla-registro-aliados.xlsx";
        MediaType type = csv ? new MediaType("text", "csv", StandardCharsets.UTF_8)
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(type)
                .body(body);
    }
}
