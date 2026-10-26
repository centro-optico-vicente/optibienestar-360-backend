package com.fenixcore.optibienestar360.modules.media;

import com.fenixcore.optibienestar360.modules.ally.media.AllyMediaOwner;
import com.fenixcore.optibienestar360.modules.ally.media.AllyProductMediaOwner;
import com.fenixcore.optibienestar360.modules.media.dto.MediaExternalRequest;
import com.fenixcore.optibienestar360.modules.media.dto.MediaItemDto;
import com.fenixcore.optibienestar360.modules.media.dto.MediaReorderRequest;
import com.fenixcore.optibienestar360.modules.media.dto.MediaUpdateRequest;
import com.fenixcore.optibienestar360.modules.media.service.MediaItemsService;
import com.fenixcore.optibienestar360.modules.media.service.MediaOwnerRegistry;
import com.fenixcore.optibienestar360.modules.product.media.ProductMediaOwner;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Galleries of allies, products and ally offers (hub ADR 0027): the same six
 * actions on every owner, so one controller serves five routes. The route tells
 * the owner and the scope ({@code /v1/admin/**} → granular permissions,
 * {@code /v1/me/**} → ally membership); the authorization itself lives in each
 * {@link com.fenixcore.optibienestar360.modules.media.service.MediaOwner}, which
 * is why the handlers only require an authenticated user.
 *
 * <pre>
 *   GET    {base}                  list, in display order
 *   POST   {base}                  multipart: file (+ caption) — published immediately
 *   POST   {base}/url              {url, caption} — https link
 *   PATCH  {base}/{mediaUuid}      {caption, primary}
 *   PUT    {base}/order            {uuids} — the full list in the new order
 *   DELETE {base}/{mediaUuid}
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class MediaController {

    private static final String ALLY = "/v1/admin/allies/{allyUuid}/media";
    private static final String PRODUCT = "/v1/admin/products/{productUuid}/media";
    private static final String OFFER = "/v1/admin/allies/{allyUuid}/products/{offerUuid}/media";
    private static final String ME_ALLY = "/v1/me/allies/{allyUuid}/media";
    private static final String ME_OFFER = "/v1/me/allies/products/{offerUuid}/media";

    private final MediaItemsService mediaService;
    private final MediaOwnerRegistry owners;

    @GetMapping({ALLY, PRODUCT, OFFER, ME_ALLY, ME_OFFER})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MediaItemDto>> list(HttpServletRequest request,
                                                   @PathVariable Map<String, String> vars,
                                                   @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(mediaService.list(target(request, vars), actor));
    }

    @PostMapping(value = {ALLY, PRODUCT, OFFER, ME_ALLY, ME_OFFER}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaItemDto> upload(HttpServletRequest request,
                                               @PathVariable Map<String, String> vars,
                                               @RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) String caption,
                                               @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(mediaService.upload(target(request, vars), actor, file, caption));
    }

    @PostMapping({ALLY + "/url", PRODUCT + "/url", OFFER + "/url", ME_ALLY + "/url", ME_OFFER + "/url"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaItemDto> addUrl(HttpServletRequest request,
                                               @PathVariable Map<String, String> vars,
                                               @Valid @RequestBody MediaExternalRequest body,
                                               @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(mediaService.addExternal(target(request, vars), actor, body.url(), body.caption()));
    }

    @PatchMapping({ALLY + "/{mediaUuid}", PRODUCT + "/{mediaUuid}", OFFER + "/{mediaUuid}",
            ME_ALLY + "/{mediaUuid}", ME_OFFER + "/{mediaUuid}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MediaItemDto> update(HttpServletRequest request,
                                               @PathVariable Map<String, String> vars,
                                               @Valid @RequestBody MediaUpdateRequest body,
                                               @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(mediaService.update(target(request, vars), actor,
                uuid(vars, "mediaUuid"), body.caption(), body.primary()));
    }

    @PutMapping({ALLY + "/order", PRODUCT + "/order", OFFER + "/order", ME_ALLY + "/order", ME_OFFER + "/order"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MediaItemDto>> reorder(HttpServletRequest request,
                                                      @PathVariable Map<String, String> vars,
                                                      @Valid @RequestBody MediaReorderRequest body,
                                                      @AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(mediaService.reorder(target(request, vars), actor, body.uuids()));
    }

    @DeleteMapping({ALLY + "/{mediaUuid}", PRODUCT + "/{mediaUuid}", OFFER + "/{mediaUuid}",
            ME_ALLY + "/{mediaUuid}", ME_OFFER + "/{mediaUuid}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(HttpServletRequest request,
                                       @PathVariable Map<String, String> vars,
                                       @AuthenticationPrincipal CustomUserDetails actor) {
        mediaService.delete(target(request, vars), actor, uuid(vars, "mediaUuid"));
        return ResponseEntity.noContent().build();
    }

    // ─── Route → target ──────────────────────────────────────────────────────

    /** The path variables present say which owner the route is for; {@code /v1/me/} says the portal. */
    private MediaTarget target(HttpServletRequest request, Map<String, String> vars) {
        MediaScope scope = request.getRequestURI().contains("/v1/me/") ? MediaScope.PORTAL : MediaScope.ADMIN;
        if (vars.containsKey("offerUuid")) {
            UUID parent = vars.containsKey("allyUuid") ? uuid(vars, "allyUuid") : null;
            return new MediaTarget(owners.byKey(AllyProductMediaOwner.KEY), uuid(vars, "offerUuid"), scope, parent);
        }
        if (vars.containsKey("productUuid")) {
            return new MediaTarget(owners.byKey(ProductMediaOwner.KEY), uuid(vars, "productUuid"), scope, null);
        }
        return new MediaTarget(owners.byKey(AllyMediaOwner.KEY), uuid(vars, "allyUuid"), scope, null);
    }

    /** A malformed uuid in the path is a missing resource, not a server error. */
    private static UUID uuid(Map<String, String> vars, String name) {
        try {
            return UUID.fromString(vars.get(name));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new NoSuchElementException("media.not_found");
        }
    }
}
