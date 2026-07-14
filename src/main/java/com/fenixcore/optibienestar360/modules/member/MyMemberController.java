package com.fenixcore.optibienestar360.modules.member;

import com.fenixcore.optibienestar360.modules.member.dto.MemberDetailDto;
import com.fenixcore.optibienestar360.modules.member.service.MembersService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service "my member record" surface for the JWT-authenticated user.
 *
 * <p>{@link MemberDetailDto} is reused verbatim from the admin endpoint —
 * the response is the user's own data, so admin audit metadata (created_at,
 * updated_at, status, active) is acceptable to expose. The
 * {@code MEMBER_VIEW_OWN} permission gating this route is granted by the
 * V6 seed to the {@code AFILIADO} role only, so a staff user (no member
 * row) will be 403'd at the {@code @PreAuthorize} layer before reaching
 * the service, and a future re-grant to another role still relies on the
 * {@code findByUserUuid} lookup to scope to the caller — there's no path
 * for one user to see another's member record through this controller.</p>
 *
 * <p>404 paths:</p>
 * <ul>
 *   <li>The user has {@code MEMBER_VIEW_OWN} but is not enrolled (no
 *       {@link com.fenixcore.optibienestar360.modules.member.entity.Member Member}
 *       row points at their person) — {@code me.member.not_enrolled}.</li>
 *   <li>The user's member row is soft-deleted ({@code is_active=false}) —
 *       same 404, same message; from the affiliate's perspective the
 *       enrollment is "gone".</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/me/member")
@RequiredArgsConstructor
public class MyMemberController {

    private final MembersService membersService;

    @GetMapping
    @PreAuthorize("hasAuthority('MEMBER_VIEW_OWN')")
    public ResponseEntity<MemberDetailDto> getMine(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(membersService.getMyMember(actor.getUuid()));
    }
}
