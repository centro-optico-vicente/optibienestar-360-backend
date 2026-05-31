package com.fenixcore.optisaludplus.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@ConditionalOnProperty(name = "auth.token-blacklist.provider", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String REFRESH_PREFIX   = "refresh:";
    private static final String USER_REFRESH_SET = "user_refresh:";

    private final StringRedisTemplate redis;

    @Override
    public void blacklistAccessToken(String jti, long ttlSeconds) {
        try {
            redis.opsForValue().set(BLACKLIST_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis unavailable — access token {} not blacklisted", jti);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            // Fail-closed: Redis outage must not allow revoked tokens through
            log.warn("Redis unavailable — rejecting token (fail-closed): jti={}", jti);
            return true;
        }
    }

    @Override
    public void storeRefreshToken(String jti, String userUuid, long ttlSeconds) {
        final Duration ttl = Duration.ofSeconds(ttlSeconds);
        final String refreshKey = REFRESH_PREFIX + jti;
        final String userSetKey = USER_REFRESH_SET + userUuid;
        try {
            // MULTI/EXEC keeps the three writes atomic on the Redis side: either
            // all three apply or none does. Without the transaction, a crash or
            // failover between the SADD and the EXPIRE leaves the per-user set
            // with no TTL → it leaks forever; a crash between the SET and the
            // SADD leaves the refresh token usable but invisible to
            // revokeAllUserRefreshTokens. Pipelining alone would only batch the
            // round-trips, not give atomicity, so MULTI/EXEC is the fix.
            redis.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public List<Object> execute(RedisOperations operations) {
                    operations.multi();
                    operations.opsForValue().set(refreshKey, userUuid, ttl);
                    operations.opsForSet().add(userSetKey, jti);
                    operations.expire(userSetKey, ttl);
                    return operations.exec();
                }
            });
        } catch (Exception e) {
            log.warn("Redis unavailable — refresh token {} not stored", jti);
        }
    }

    @Override
    public boolean isValidRefreshToken(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(REFRESH_PREFIX + jti));
        } catch (Exception e) {
            log.warn("Redis unavailable — assuming refresh token is invalid");
            return false;
        }
    }

    @Override
    public String getRefreshTokenUser(String jti) {
        try {
            return redis.opsForValue().get(REFRESH_PREFIX + jti);
        } catch (Exception e) {
            log.warn("Redis unavailable — cannot retrieve refresh token user");
            return null;
        }
    }

    @Override
    public void revokeRefreshToken(String jti, String userUuid) {
        try {
            redis.delete(REFRESH_PREFIX + jti);
            redis.opsForSet().remove(USER_REFRESH_SET + userUuid, jti);
        } catch (Exception e) {
            log.warn("Redis unavailable — refresh token {} not revoked", jti);
        }
    }

    @Override
    public void revokeAllUserRefreshTokens(String userUuid) {
        try {
            Set<String> jtis = redis.opsForSet().members(USER_REFRESH_SET + userUuid);
            if (jtis != null && !jtis.isEmpty()) {
                List<String> keys = jtis.stream().map(jti -> REFRESH_PREFIX + jti).toList();
                redis.delete(keys);
            }
            redis.delete(USER_REFRESH_SET + userUuid);
        } catch (Exception e) {
            log.warn("Redis unavailable — could not revoke all refresh tokens for user {}", userUuid);
        }
    }
}
