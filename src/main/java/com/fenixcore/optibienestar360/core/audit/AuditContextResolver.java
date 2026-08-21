package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the current actor's {@code users_id} (BIGINT) for
 * {@code data_change_audit_log.actor_id}. Not reusable from
 * {@link CustomUserDetails#getId()} directly — the JWT-backed principal built
 * by {@code JwtAuthenticationFilter} via {@code CustomUserDetails.fromJwt(...)}
 * always has a {@code null} id (no DB lookup on that hot path), so the
 * internal id has to be resolved here from the {@code uuid} claim instead.
 *
 * <p>{@code login_audit_log_id} resolution (the {@code sid} claim) isn't
 * wired yet — that requires the login hook in {@code AuthService}, still
 * pending (spec 16-audit.md §Login). Left {@code null} until then; the column
 * is nullable for exactly this kind of caller (spec §Decisiones 3).</p>
 */
@Component
@RequiredArgsConstructor
public class AuditContextResolver {

    private final UserRepository userRepository;

    public Optional<Long> resolveActorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails principal)) {
            return Optional.empty();
        }
        if (principal.getId() != null) {
            return Optional.of(principal.getId());
        }
        return userRepository.findByUuid(principal.getUuid()).map(u -> u.getId());
    }
}
