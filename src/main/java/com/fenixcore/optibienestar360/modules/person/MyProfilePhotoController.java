package com.fenixcore.optibienestar360.modules.person;

import com.fenixcore.optibienestar360.modules.person.service.ProfilePhotoService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Profile photo of the authenticated user ({@code /v1/me/photo}). Any
 * authenticated user may manage their own photo — like {@code /v1/me} itself,
 * it is scoped to the JWT subject, so no permission key is needed.
 */
@RestController
@RequestMapping("/v1/me/photo")
@RequiredArgsConstructor
public class MyProfilePhotoController {

    private final ProfilePhotoService profilePhotoService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> get(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(body(profilePhotoService.currentUrl(actor.getUuid())));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> upload(@AuthenticationPrincipal CustomUserDetails actor,
                                                      @RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(body(profilePhotoService.replace(actor.getUuid(), image)));
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserDetails actor) {
        profilePhotoService.delete(actor.getUuid());
        return ResponseEntity.noContent().build();
    }

    private static Map<String, String> body(String url) {
        Map<String, String> m = new HashMap<>();
        m.put("photoUrl", url);
        return m;
    }
}
