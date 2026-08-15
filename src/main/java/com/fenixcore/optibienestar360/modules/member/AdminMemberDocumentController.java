package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.common.storage.dto.ChecklistDto;
import com.fenixcore.optibienestar360.common.storage.dto.FileUrlDto;
import com.fenixcore.optibienestar360.modules.member.dto.MemberDocumentDto;
import com.fenixcore.optibienestar360.modules.member.entity.MemberDocument.DocumentType;
import com.fenixcore.optibienestar360.modules.member.service.MemberDocumentService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Admin surface for {@link com.fenixcore.optibienestar360.modules.member.entity.MemberDocument}
 * — cédula scans, member photo, proof of residence, etc. Spec
 * {@code .ai/specs/08-storage-r2.md} §3/§4.1.
 *
 * <p>{@code MEMBER_DOCUMENT_VIEW_ALL}/{@code _VIEW_OWN} are independent
 * authorities — {@link #list}/{@link #getUrl} accept either, and the
 * service filters to the caller's own uploads when only {@code _VIEW_OWN}
 * is held.</p>
 */
@RestController
@RequestMapping("/v1/admin/members/{memberUuid}/documents")
@RequiredArgsConstructor
public class AdminMemberDocumentController {

    private final MemberDocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MEMBER_UPLOAD_DOCUMENT')")
    public ResponseEntity<MemberDocumentDto> upload(
            @PathVariable UUID memberUuid,
            @RequestParam DocumentType documentType,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails actor) {
        MemberDocumentDto created = documentService.upload(memberUuid, documentType, file, actor.getUuid());
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{uuid}")
                .buildAndExpand(created.uuid())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('MEMBER_DOCUMENT_VIEW_ALL', 'MEMBER_DOCUMENT_VIEW_OWN')")
    public ResponseEntity<List<MemberDocumentDto>> list(
            @PathVariable UUID memberUuid,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(documentService.list(memberUuid, actor.getUuid(), hasViewAll(actor)));
    }

    /** Recaudos completeness checklist — reserved for reviewers holding {@code MEMBER_DOCUMENT_VIEW_ALL}. */
    @GetMapping("/checklist")
    @PreAuthorize("hasAuthority('MEMBER_DOCUMENT_VIEW_ALL')")
    public ResponseEntity<ChecklistDto> checklist(@PathVariable UUID memberUuid) {
        return ResponseEntity.ok(documentService.listWithChecklist(memberUuid));
    }

    @GetMapping("/{uuid}/url")
    @PreAuthorize("hasAnyAuthority('MEMBER_DOCUMENT_VIEW_ALL', 'MEMBER_DOCUMENT_VIEW_OWN')")
    public ResponseEntity<FileUrlDto> getUrl(
            @PathVariable UUID memberUuid,
            @PathVariable UUID uuid,
            @RequestParam(value = "ttlMinutes", required = false) @Min(1) @Max(60) Integer ttlMinutes) {
        Duration ttl = ttlMinutes != null ? Duration.ofMinutes(ttlMinutes) : null;
        return ResponseEntity.ok(documentService.presignedUrl(uuid, ttl));
    }

    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasAuthority('MEMBER_DOCUMENT_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable UUID memberUuid, @PathVariable UUID uuid) {
        documentService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    private static boolean hasViewAll(CustomUserDetails actor) {
        return actor.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("MEMBER_DOCUMENT_VIEW_ALL"::equals);
    }
}
