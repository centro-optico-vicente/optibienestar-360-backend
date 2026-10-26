package com.fenixcore.optibienestar360.modules.ally;

import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyDto;
import com.fenixcore.optibienestar360.modules.ally.service.MyAlliesService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.service.AllyProductsProposeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Self-service "which allies do I operate on?" surface, completing the
 * {@code /v1/me/*} family alongside {@code /v1/me/member} and
 * {@code /v1/me/payments}. {@code /v1/me/*} is the primary prefix for the
 * caller's own data; {@code /v1/allies/me} is a secondary path next to the
 * ally-portal family ({@code /v1/me/allies/services},
 * {@code /v1/me/allies/validations}…), hub spec 12.
 *
 * <p>This is the entry point of the counter flow: the portal calls it once
 * on load to resolve the caller's {@code allyUuid}, then feeds that UUID to
 * {@code POST /v1/me/allies/benefit-usages} (and {@code POST /v1/me/allies/services}),
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
    private final AllyProductsProposeService productsService;

    /**
     * The ally's own offers in every review state — the portal's "my products
     * and services" list (hub ADR 0026), where each offer's gallery
     * ({@code /v1/me/allies/products/{uuid}/media}) is managed. Any active member
     * of the ally may read it.
     */
    @GetMapping("/{allyUuid}/products")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<AllyProductDto>> myProducts(@PathVariable UUID allyUuid,
                                                           @RequestParam(required = false) String q,
                                                           @RequestParam(required = false) String type,
                                                           @PageableDefault(size = 20) Pageable pageable,
                                                           @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(productsService.listOwn(actor.getUuid(), allyUuid, q, type, pageable));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ALLY_VIEW_OWN')")
    public ResponseEntity<List<MyAllyDto>> listMine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(myAlliesService.listMyAllies(actor.getUuid()));
    }
}
