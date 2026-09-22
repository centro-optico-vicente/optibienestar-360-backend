package com.fenixcore.optibienestar360.modules.terms;

import com.fenixcore.optibienestar360.modules.terms.dto.PublicTermsDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** 404 if the current version of {@code type} exists but isn't marked public. */
    @GetMapping("/{type}")
    public ResponseEntity<PublicTermsDto> current(@PathVariable TermType type) {
        TermsVersion version = service.getCurrentPublic(type);
        return ResponseEntity.ok(new PublicTermsDto(
                version.getTermType(), version.getTitle(), version.getContentMarkdown(), version.getValidFrom()));
    }
}
