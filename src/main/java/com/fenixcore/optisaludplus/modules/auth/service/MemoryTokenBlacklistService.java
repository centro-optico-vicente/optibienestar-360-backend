package com.fenixcore.optisaludplus.modules.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * In-process implementation of {@link TokenBlacklistService} backed by Caffeine.
 *
 * <p>State is per-process: it does not survive a restart and is not shared across
 * replicas. Suitable for single-replica deployments where a Redis sidecar would
 * be unnecessary overhead. For HA / multi-replica, use {@link RedisTokenBlacklistService}.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "auth.token-blacklist.provider", havingValue = "memory")
public class MemoryTokenBlacklistService implements TokenBlacklistService {

    private final Cache<String, TtlValue> blacklist;
    private final Cache<String, TtlValue> refreshTokens;

    public MemoryTokenBlacklistService() {
        Expiry<String, TtlValue> perEntryExpiry = new Expiry<>() {
            @Override
            public long expireAfterCreate(String key, TtlValue value, long currentTime) {
                return value.ttlNanos();
            }
            @Override
            public long expireAfterUpdate(String key, TtlValue value, long currentTime, long currentDuration) {
                return value.ttlNanos();
            }
            @Override
            public long expireAfterRead(String key, TtlValue value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };

        this.blacklist     = Caffeine.newBuilder().expireAfter(perEntryExpiry).build();
        this.refreshTokens = Caffeine.newBuilder().expireAfter(perEntryExpiry).build();
        log.info("Token blacklist provider: memory (Caffeine). Multi-replica logout/revoke will NOT propagate.");
    }

    @Override
    public void blacklistAccessToken(String jti, long ttlSeconds) {
        blacklist.put(jti, new TtlValue("1", Duration.ofSeconds(ttlSeconds).toNanos()));
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return blacklist.getIfPresent(jti) != null;
    }

    @Override
    public void storeRefreshToken(String jti, String userUuid, long ttlSeconds) {
        refreshTokens.put(jti, new TtlValue(userUuid, Duration.ofSeconds(ttlSeconds).toNanos()));
    }

    @Override
    public boolean isValidRefreshToken(String jti) {
        return refreshTokens.getIfPresent(jti) != null;
    }

    @Override
    public String getRefreshTokenUser(String jti) {
        TtlValue entry = refreshTokens.getIfPresent(jti);
        return entry != null ? entry.value() : null;
    }

    @Override
    public void revokeRefreshToken(String jti, String userUuid) {
        refreshTokens.invalidate(jti);
    }

    @Override
    public void revokeAllUserRefreshTokens(String userUuid) {
        // O(n) scan over all live refresh tokens. revokeAll is a low-frequency
        // operation (password change, admin force-logout) so the cost is acceptable
        // at the user-count scale of a single-replica deployment.
        for (Map.Entry<String, TtlValue> entry : refreshTokens.asMap().entrySet()) {
            if (userUuid.equals(entry.getValue().value())) {
                refreshTokens.invalidate(entry.getKey());
            }
        }
    }

    /** Pair of (stored value, per-entry TTL in nanos) consumed by Caffeine's {@link Expiry}. */
    private record TtlValue(String value, long ttlNanos) {
        TtlValue {
            if (ttlNanos < 0) {
                ttlNanos = 0;
            }
            // Cap at Long.MAX_VALUE / 2 to avoid overflow inside Caffeine's scheduler arithmetic.
            long maxNanos = TimeUnit.DAYS.toNanos(3650);  // 10 years — way over any sensible JWT TTL
            if (ttlNanos > maxNanos) {
                ttlNanos = maxNanos;
            }
        }
    }
}
