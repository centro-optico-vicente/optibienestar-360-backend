package com.fenixcore.optibienestar360.modules.terms;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.terms.dto.MemberTermsPreviewRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.PublicTermsDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.MemberContractService;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Anonymous-facing T&C, e.g. for a public "Terms" page linked from the
 * landing footer outside the authenticated app. Mounted under
 * {@code /v1/public/**}, already {@code permitAll()} in
 * {@code SecurityConfig.PUBLIC_PATHS} — nothing to add there.
 */
@RestController
@RequestMapping("/v1/public/terms")
@RequiredArgsConstructor
public class PublicTermsController {

    private final TermsVersionService service;
    private final MemberContractService memberContractService;

    /**
     * Every current public version, one per stream (AFILIADO, ASESOR, and the
     * ALIADO B2B and B2P agreements) — what a public site lists.
     */
    @GetMapping
    public ResponseEntity<List<PublicTermsDto>> list() {
        return ResponseEntity.ok(service.listCurrentPublicRendered());
    }

    /**
     * The current version of {@code type}; 404 if it isn't marked public.
     * ALIADO is split by {@code segment} ({@code B2B} / {@code B2P}): the
     * parameter is required once segment templates exist (422 otherwise) and
     * rejected (422) on any other type.
     */
    @GetMapping("/{type}")
    public ResponseEntity<PublicTermsDto> current(@PathVariable TermType type,
                                                  @RequestParam(required = false) AllyType.Segment segment) {
        return ResponseEntity.ok(service.getCurrentPublicRendered(type, segment));
    }

    /**
     * The current public contract rendered with the data typed on the public
     * affiliation form so far (live preview). Saves nothing; only AFILIADO
     * (422 for other kinds). A POST because it carries personal data that
     * must not end up in URLs or access logs.
     */
    @PostMapping("/{type}/preview")
    public ResponseEntity<PublicTermsDto> preview(@PathVariable TermType type,
                                                  @Valid @RequestBody MemberTermsPreviewRequest request) {
        return ResponseEntity.ok(memberContractService.preview(type, request));
    }
}
