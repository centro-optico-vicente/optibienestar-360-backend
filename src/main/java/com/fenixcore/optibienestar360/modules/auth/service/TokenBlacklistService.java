package com.fenixcore.optibienestar360.modules.auth.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores revoked access tokens (blacklist) and active refresh tokens with TTL.
 *
 * <p>Two implementations are wired via {@code auth.token-blacklist.provider}:
 * <ul>
 *   <li>{@code redis} (default) — {@link RedisTokenBlacklistService}, suitable for HA
 *       and multi-replica deployments.</li>
 *   <li>{@code memory} — {@link MemoryTokenBlacklistService}, Caffeine-backed,
 *       suitable for single-replica deployments. State is per-process; a logout in
 *       one replica does not invalidate tokens in another.</li>
 * </ul>
 */
public interface TokenBlacklistService {

    void blacklistAccessToken(String jti, long ttlSeconds);

    boolean isBlacklisted(String jti);

    void storeRefreshToken(String jti, String userUuid, long ttlSeconds);

    boolean isValidRefreshToken(String jti);

    String getRefreshTokenUser(String jti);

    void revokeRefreshToken(String jti, String userUuid);

    void revokeAllUserRefreshTokens(String userUuid);

    // ─── User invalidation epoch ────────────────────────────────────────────
    // Used by RoleService / UserService when a role's permissions or a user's
    // role assignment changes. Stores "now" as the user's invalidation epoch;
    // the JwtAuthenticationFilter rejects any access token whose iat is older
    // than this epoch, forcing the client to refresh and pick up the new
    // permissions. Avoids the up-to-15-min staleness window of the access
    // token TTL without having to track per-user active JTIs server-side.

    /**
     * Mark every access token of {@code userUuid} issued before {@link java.time.Instant#now()}
     * as stale. Idempotent. The marker auto-expires after a TTL chosen to
     * comfortably outlive any possible access token (impl-defined).
     */
    void markUserInvalidatedNow(String userUuid);

    /**
     * @return the epoch seconds of the most recent {@link #markUserInvalidatedNow(String)}
     *         for this user, or {@code 0} if no marker is stored (no invalidation
     *         ever recorded, or the marker has expired).
     */
    long getUserInvalidatedEpoch(String userUuid);

    // ─── Restart recovery (volatile stores only) ────────────────────────────
    // A store that loses its state on restart (memory) cannot know the refresh
    // tokens issued before the process started. AuthService.refresh() then falls
    // back to the persisted session (login_audit_log) for those tokens only — see
    // AuthService#recoverAfterRestart. Durable stores (redis) never use it.

    /** {@code true} when this store's state does not survive a process restart. */
    default boolean isVolatile() {
        return false;
    }

    /** When this store's state began — tokens issued before it cannot be in the store. */
    default Instant startedAt() {
        return Instant.EPOCH;
    }

    /**
     * Records that a session was recovered after a restart.
     *
     * @return {@code true} the first time for this session, {@code false} if it was already
     *         recovered — so a second pre-restart token of the same session (one already
     *         rotated before the restart) is rejected.
     */
    default boolean markSessionRecovered(UUID sessionId) {
        return false;
    }
}
