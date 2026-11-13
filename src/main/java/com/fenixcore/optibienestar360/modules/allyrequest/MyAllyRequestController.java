package com.fenixcore.optibienestar360.modules.allyrequest;

import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyRequestCreatedDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.MyAllyRequestCreateRequest;
import com.fenixcore.optibienestar360.modules.allyrequest.service.AllyRequestService;
import com.fenixcore.optibienestar360.modules.auth.dto.LoginResponse;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /v1/me/ally-requests} — someone who already has an account (a
 * member with a business, an advisor with a practice) asks to become an ally
 * without a second account: the PENDING ally is created on their own person
 * and login, which gains {@code ALIADO_PENDIENTE}. Any authenticated caller
 * may ask, whatever their active role. The response carries a new session
 * already switched to the pending role (same shape as {@code POST
 * /v1/me/active-role}), so the portal goes on with the ally's own steps; the
 * previous session is closed, as on any role switch.
 */
@RestController
@RequestMapping("/v1/me/ally-requests")
@RequiredArgsConstructor
public class MyAllyRequestController {

    private final AllyRequestService service;
    private final AuthService authService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public AllyRequestCreatedDto create(@Valid @RequestBody MyAllyRequestCreateRequest request,
                                        @AuthenticationPrincipal CustomUserDetails actor,
                                        HttpServletRequest httpRequest) {
        AllyRequestCreatedDto created = service.createForAccount(actor.getUuid(), request);
        LoginResponse session = authService.switchActiveRole(actor.getUuid(), service.pendingRole().getUuid(),
                actor.getJti(), actor.getSessionId(), request.refreshToken(), httpRequest);
        return new AllyRequestCreatedDto(created.allyUuid(), created.registrationStatus(), created.email(),
                created.promoterName(), session);
    }
}
