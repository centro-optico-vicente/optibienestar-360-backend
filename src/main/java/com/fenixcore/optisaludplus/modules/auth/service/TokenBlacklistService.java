package com.fenixcore.optisaludplus.modules.auth.service;

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
}
