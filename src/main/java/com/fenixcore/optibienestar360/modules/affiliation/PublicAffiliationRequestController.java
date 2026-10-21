package com.fenixcore.optibienestar360.modules.affiliation;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.service.AffiliationRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public self-affiliation from the advisor's QR
 * ({@code POST /v1/public/affiliation-requests}). Stores a PENDING request
 * that staff review ({@code /v1/admin/affiliation-requests}); the AFILIADO
 * login, the member and the membership are created only on approval (see
 * {@link AffiliationRequestService}). {@code termsVersionUuid} is the current
 * AFILIADO contract the applicant accepted; the text rendered with their data,
 * its sha256 and the request IP/User-Agent are stored as evidence.
 * Under {@code /v1/public/**}, already {@code permitAll()}.
 */
@RestController
@RequestMapping("/v1/public")
@RequiredArgsConstructor
public class PublicAffiliationRequestController {

    private final AffiliationRequestService service;

    // Deprecated alias, remove once frontend >= #240 is deployed
    @PostMapping({"/affiliation-requests", "/affiliations"})
    @ResponseStatus(HttpStatus.CREATED)
    public AffiliationRequestCreatedDto create(@Valid @RequestBody AffiliationRequestCreateRequest request,
                                               HttpServletRequest httpRequest) {
        return service.create(request, ClientRequestMetadata.from(httpRequest));
    }
}
