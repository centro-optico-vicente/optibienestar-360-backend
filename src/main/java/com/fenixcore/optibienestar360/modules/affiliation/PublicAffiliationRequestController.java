package com.fenixcore.optibienestar360.modules.affiliation;

import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.affiliation.dto.AffiliationRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.affiliation.service.AffiliationRequestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public self-affiliation from the advisor's QR
 * ({@code POST /v1/public/affiliation-requests}). Stores a PENDING request
 * that staff review ({@code /v1/admin/affiliation-requests}); the AFILIADO
 * login, the member and the membership are created only on approval (see
 * {@link AffiliationRequestService}). {@code termsVersionUuid} is the current
 * AFILIADO contract the applicant accepted; the text rendered with their data,
 * its sha256 and the request IP/User-Agent are stored as evidence.
 *
 * <p>The optional {@code payment} reports the payment of the plan's
 * inscription fee. Sent as JSON it carries no proof; sent as
 * {@code multipart/form-data} the body is the {@code request} part
 * (application/json) plus an optional {@code support} part with the proof
 * file — same pattern as {@code POST /v1/admin/payments}.</p>
 * Under {@code /v1/public/**}, already {@code permitAll()}.
 */
@RestController
@RequestMapping("/v1/public")
@RequiredArgsConstructor
public class PublicAffiliationRequestController {

    private final AffiliationRequestService service;

    @PostMapping("/affiliation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AffiliationRequestCreatedDto create(@Valid @RequestBody AffiliationRequestCreateRequest request,
                                               HttpServletRequest httpRequest) {
        return service.create(request, ClientRequestMetadata.from(httpRequest));
    }

    @PostMapping(value = "/affiliation-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AffiliationRequestCreatedDto createMultipart(
            @Valid @RequestPart("request") AffiliationRequestCreateRequest request,
            @RequestPart(value = "support", required = false) MultipartFile support,
            HttpServletRequest httpRequest) {
        return service.create(request, support, ClientRequestMetadata.from(httpRequest));
    }
}
