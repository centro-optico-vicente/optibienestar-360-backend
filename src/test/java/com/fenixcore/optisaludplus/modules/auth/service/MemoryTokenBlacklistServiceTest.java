package com.fenixcore.optisaludplus.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MemoryTokenBlacklistServiceTest {

    private MemoryTokenBlacklistService svc;

    @BeforeEach
    void setUp() {
        svc = new MemoryTokenBlacklistService();
    }

    @Test
    void blacklistedTokenIsDetected() {
        String jti = UUID.randomUUID().toString();
        svc.blacklistAccessToken(jti, 60);

        assertThat(svc.isBlacklisted(jti)).isTrue();
        assertThat(svc.isBlacklisted("other-jti")).isFalse();
    }

    @Test
    void blacklistEntryExpiresAfterTtl() {
        String jti = UUID.randomUUID().toString();
        svc.blacklistAccessToken(jti, 1);

        assertThat(svc.isBlacklisted(jti)).isTrue();
        await().atMost(3, TimeUnit.SECONDS).until(() -> !svc.isBlacklisted(jti));
    }

    @Test
    void refreshTokenStoredAndRetrieved() {
        String jti = UUID.randomUUID().toString();
        String userUuid = UUID.randomUUID().toString();

        svc.storeRefreshToken(jti, userUuid, 60);

        assertThat(svc.isValidRefreshToken(jti)).isTrue();
        assertThat(svc.getRefreshTokenUser(jti)).isEqualTo(userUuid);
    }

    @Test
    void refreshTokenExpiresAfterTtl() {
        String jti = UUID.randomUUID().toString();
        svc.storeRefreshToken(jti, "user-1", 1);

        assertThat(svc.isValidRefreshToken(jti)).isTrue();
        await().atMost(3, TimeUnit.SECONDS).until(() -> !svc.isValidRefreshToken(jti));
        assertThat(svc.getRefreshTokenUser(jti)).isNull();
    }

    @Test
    void revokeSingleRefreshToken() {
        String jti = UUID.randomUUID().toString();
        svc.storeRefreshToken(jti, "user-1", 60);

        svc.revokeRefreshToken(jti, "user-1");

        assertThat(svc.isValidRefreshToken(jti)).isFalse();
    }

    @Test
    void revokeAllUserRefreshTokensRemovesOnlyThatUserTokens() {
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();
        String jtiA1 = "jtiA1", jtiA2 = "jtiA2", jtiB = "jtiB";

        svc.storeRefreshToken(jtiA1, userA, 60);
        svc.storeRefreshToken(jtiA2, userA, 60);
        svc.storeRefreshToken(jtiB,  userB, 60);

        svc.revokeAllUserRefreshTokens(userA);

        assertThat(svc.isValidRefreshToken(jtiA1)).isFalse();
        assertThat(svc.isValidRefreshToken(jtiA2)).isFalse();
        assertThat(svc.isValidRefreshToken(jtiB)).isTrue();
    }

    @Test
    void getUnknownRefreshTokenReturnsNull() {
        assertThat(svc.getRefreshTokenUser("never-stored")).isNull();
        assertThat(svc.isValidRefreshToken("never-stored")).isFalse();
    }

    @Test
    void revokeRefreshTokenIsIdempotent() {
        svc.revokeRefreshToken("nonexistent", "user-1");
        // No exception, no side effects on other entries:
        svc.storeRefreshToken("real-jti", "user-1", 60);
        svc.revokeRefreshToken("nonexistent", "user-1");
        assertThat(svc.isValidRefreshToken("real-jti")).isTrue();
    }

    @Test
    void zeroTtlExpiresImmediately() {
        svc.blacklistAccessToken("jti-0", 0);
        // Caffeine may keep the entry briefly until next maintenance cycle;
        // poll for eventual expiry rather than asserting immediately.
        await().atMost(2, TimeUnit.SECONDS).until(() -> !svc.isBlacklisted("jti-0"));
    }

    // ─── User invalidation epoch ────────────────────────────────────────────

    @Test
    void getUserInvalidatedEpoch_returnsZeroWhenNeverInvalidated() {
        assertThat(svc.getUserInvalidatedEpoch(UUID.randomUUID().toString())).isZero();
    }

    @Test
    void markUserInvalidatedNow_storesRecentEpochAndIsIdempotent() {
        String userUuid = UUID.randomUUID().toString();
        long before = java.time.Instant.now().getEpochSecond();
        svc.markUserInvalidatedNow(userUuid);
        long after = java.time.Instant.now().getEpochSecond();

        long epoch = svc.getUserInvalidatedEpoch(userUuid);
        assertThat(epoch).isBetween(before, after);

        // Re-marking is idempotent (overwrites with a newer-or-equal epoch).
        svc.markUserInvalidatedNow(userUuid);
        assertThat(svc.getUserInvalidatedEpoch(userUuid)).isGreaterThanOrEqualTo(epoch);
    }

    @Test
    void userInvalidationsAreScopedPerUser() {
        String a = UUID.randomUUID().toString();
        String b = UUID.randomUUID().toString();
        svc.markUserInvalidatedNow(a);

        assertThat(svc.getUserInvalidatedEpoch(a)).isPositive();
        assertThat(svc.getUserInvalidatedEpoch(b)).isZero();
    }
}
