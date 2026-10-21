package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyDto;
import com.fenixcore.optibienestar360.modules.ally.service.MyAlliesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Self-service "which allies do I operate on?" surface, completing the
 * {@code /v1/me/*} family alongside {@code /v1/me/member} and
 * {@code /v1/me/payments}. {@code /v1/me/*} is the primary prefix for the
 * caller's own data; {@code /v1/allies/me} is a secondary path next to the
 * ally-portal family ({@code /v1/allies/me/services},
 * {@code /v1/allies/me/validations}…), hub spec 12.
 *
 * <p>This is the entry point of the counter flow: the portal calls it once
 * on load to resolve the caller's {@code allyUuid}, then feeds that UUID to
 * {@code POST /v1/allies/me/benefit-usages} (and {@code POST /v1/allies/me/services}),
 * both of which require it in the body. Before this endpoint existed the
 * portal had no way to obtain it and asked the operator to type it.</p>
 *
 * <p>Scoping is by JWT subject, not by a path variable — there is no way to
 * ask for another user's memberships through this controller. The
 * {@code ALLY_VIEW_OWN} permission gating it is granted by the V6 seed to
 * the {@code ALIADO} role only.</p>
 *
 * <p>Always 200: a caller with no active membership gets an empty array
 * (see {@link MyAlliesService#listMyAllies}).</p>
 */
@RestController
@RequestMapping({"/v1/me/allies", "/v1/allies/me"})
@RequiredArgsConstructor
public class MyAlliesController {

    private final MyAlliesService myAlliesService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_OWN')")
    public ResponseEntity<List<MyAllyDto>> listMine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(myAlliesService.listMyAllies(actor.getUuid()));
    }
}
