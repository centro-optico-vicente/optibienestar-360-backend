package com.fenixcore.optibienestar360.modules.birthday;

import com.fenixcore.optibienestar360.modules.birthday.dto.MyBirthdaysDto;
import com.fenixcore.optibienestar360.modules.birthday.service.BirthdayService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Birthday notices of the portals ({@code GET /v1/me/birthdays}). Like the home
 * dashboard it has no permission of its own: {@link BirthdayService} adds the
 * caller's own greeting only with {@code MEMBER_VIEW_OWN} and the advisor's
 * portfolio birthdays only with {@code PROMOTER_VIEW_OWN}, the permissions that
 * already protect that data.
 */
@RestController
@RequestMapping("/v1/me/birthdays")
@RequiredArgsConstructor
public class MyBirthdaysController {

    private final BirthdayService birthdayService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MyBirthdaysDto> mine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(birthdayService.getMyBirthdays(actor.getUuid(),
                actor.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()));
    }
}
