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
 * Self-service "which allies do I operate on?" surface. The primary path is
 * {@code /v1/allies/me}, the root of the ally-portal family
 * ({@code /v1/allies/me/services}, {@code /v1/allies/me/benefit-usages},
 * {@code /v1/allies/me/validations}, {@code /v1/allies/me/usage-history}).
 * {@code /v1/me/allies} is the original path, kept as a deprecated alias.
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
// Deprecated alias, remove once frontend >= #238 is deployed
@RequestMapping({"/v1/allies/me", "/v1/me/allies"})
@RequiredArgsConstructor
public class MyAlliesController {

    private final MyAlliesService myAlliesService;

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_OWN')")
    public ResponseEntity<List<MyAllyDto>> listMine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(myAlliesService.listMyAllies(actor.getUuid()));
    }
}
