package com.fenixcore.optisaludplus.modules.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
        try {
            redis.opsForValue().set(REFRESH_PREFIX + jti, userUuid, Duration.ofSeconds(ttlSeconds));
            redis.opsForSet().add(USER_REFRESH_SET + userUuid, jti);
            redis.expire(USER_REFRESH_SET + userUuid, Duration.ofSeconds(ttlSeconds));
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
