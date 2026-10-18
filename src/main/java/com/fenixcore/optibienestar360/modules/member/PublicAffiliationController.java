package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.core.validation.VenezuelanDocumentNumber;
import com.fenixcore.optibienestar360.core.web.ClientRequestMetadata;
import com.fenixcore.optibienestar360.modules.member.service.PublicAffiliationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Self-service affiliation from the promoter's QR ({@code POST /v1/public/affiliations}).
 * Creates the AFILIADO login, the member attributed to the referral code's
 * promoter, and the chosen plan's membership — SUSPENDED until the first
 * payment is approved (see {@link PublicAffiliationService}).
 * {@code termsVersionUuid} is the current AFILIADO T&C the applicant accepted
 * ({@code GET /v1/public/terms/AFILIADO}); the acceptance is recorded with
 * channel {@code SELF_SERVICE} and the request IP/User-Agent as evidence.
 */
@RestController
@RequestMapping("/v1/public/affiliations")
@RequiredArgsConstructor
public class PublicAffiliationController {

    private final PublicAffiliationService service;

    public record PublicAffiliationRequest(
            @NotBlank @Size(max = 50) String firstName,
            @NotBlank @Size(max = 50) String lastName,
            @NotBlank @Pattern(regexp = "^[VE]$") String documentType,
            @NotBlank @Size(max = 20) @VenezuelanDocumentNumber String documentNumber,
            @NotNull @Past LocalDate birthDate,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotNull UUID planUuid,
            @Size(max = 20) String referralCode,
            @NotNull UUID termsVersionUuid
    ) {}

    public record PublicAffiliationResponse(UUID memberUuid, String email, String promoterName) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicAffiliationResponse create(@Valid @RequestBody PublicAffiliationRequest request,
                                            HttpServletRequest httpRequest) {
        return service.create(request, ClientRequestMetadata.from(httpRequest));
    }
}
