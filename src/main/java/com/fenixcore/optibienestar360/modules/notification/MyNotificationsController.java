package com.fenixcore.optibienestar360.modules.notification;

import com.fenixcore.optibienestar360.modules.notification.dto.UnreadCountDto;
import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationDto;
import com.fenixcore.optibienestar360.modules.notification.service.UserNotificationService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The caller's in-app notification inbox (V259, hub ADR 0039). Any
 * authenticated session, no catalog permission: every endpoint is scoped by
 * the JWT subject (same posture as {@code MyTermsController}). Not behind the
 * portal contract gate, so the frontend can poll {@code /unread-count} from
 * any role.
 *
 * <ul>
 *   <li>{@code GET ?unread=true&category=ACCOUNT&page=0&size=20} — standard
 *       page, newest first, expired rows left out. {@code unread=true} keeps
 *       only the unread ones; {@code false} or absent returns all.</li>
 *   <li>{@code GET /unread-count} → {@code {"count": n}} — cheap, polled.</li>
 *   <li>{@code POST /{uuid}/read} → 204, idempotent; 404 when not the caller's.</li>
 *   <li>{@code POST /read-all} → 204.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/me/notifications")
@RequiredArgsConstructor
public class MyNotificationsController {

    private final UserNotificationService service;

    @GetMapping
    public ResponseEntity<Page<UserNotificationDto>> list(
            @AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.listMine(actor.getUuid(), Boolean.TRUE.equals(unread), category, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountDto> unreadCount(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok(new UnreadCountDto(service.unreadCount(actor.getUuid())));
    }

    @PostMapping("/{uuid}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal CustomUserDetails actor, @PathVariable UUID uuid) {
        service.markRead(actor.getUuid(), uuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal CustomUserDetails actor) {
        service.markAllRead(actor.getUuid());
        return ResponseEntity.noContent().build();
    }
}
