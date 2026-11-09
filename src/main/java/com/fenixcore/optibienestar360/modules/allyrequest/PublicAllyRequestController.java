package com.fenixcore.optibienestar360.modules.allyrequest;

import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.allyrequest.service.AllyRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public "Solicitar alianza" form ({@code POST /v1/public/ally-requests}, hub
 * ADR 0036): creates the ally in PENDING and its portal login with the chosen
 * password; the portal opens right away, the directory only after staff
 * approve it. Under {@code /v1/public/**}, already {@code permitAll()};
 * throttled per IP by {@code PublicSubmissionRateLimitFilter}.
 */
@RestController
@RequestMapping("/v1/public")
@RequiredArgsConstructor
public class PublicAllyRequestController {

    private final AllyRequestService service;

    @PostMapping("/ally-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AllyRequestCreatedDto create(@Valid @RequestBody AllyRequestCreateRequest request,
                                        jakarta.servlet.http.HttpServletRequest httpRequest) {
        return service.create(request, httpRequest);
    }
}
