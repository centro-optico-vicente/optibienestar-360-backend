package com.fenixcore.optibienestar360.modules.validator.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Eviction surface for the realtime ally-validator cache. The validator
 * endpoint ({@code GET /v1/ally/validate/{document}}, future bullet) reads
 * a short-lived solvency snapshot from Redis keyed by
 * {@code validator:document:{type}:{number}}; this service is the
 * counterpart that DELs the key whenever something happens that could
 * invalidate the cached answer (membership status flips, payment is
 * approved or rejected, affiliate re-enrolls, etc.).
 *
 * <p><b>Redis availability:</b> Spring Boot auto-configures
 * {@link StringRedisTemplate} when {@code spring-boot-starter-data-redis}
 * is on the classpath. The token-blacklist's
 * {@code TokenBlacklistEnvironmentPostProcessor} can exclude Redis
 * auto-config when running with the in-memory token blacklist provider;
 * in that mode the {@link ObjectProvider} returns empty here and every
 * call is silently no-op. The cache producer (validator endpoint) follows
 * the same pattern, so the system degrades gracefully in dev / single-node
 * environments without Redis.</p>
 *
 * <p><b>Failure policy:</b> Redis exceptions are logged + swallowed. The
 * upstream state change (the membership transition / payment review) has
 * already committed; a Redis hiccup must never roll that back. Worst case
 * the cache holds a stale entry for up to the TTL (60s per vertical-7
 * spec), which is short-lived and self-healing.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidatorCacheService {

    public static final String KEY_PREFIX = "validator:document:";

    private final ObjectProvider<StringRedisTemplate> redisProvider;

    /**
     * Canonical Redis key for the validator entry of a given document.
     * Returned in a stable shape ({@code validator:document:V:12345678})
     * so the validator endpoint (future) and this evictor agree on the
     * exact spelling — single source of truth.
     */
    public String keyFor(String documentType, String documentNumber) {
        return KEY_PREFIX + documentType + ":" + documentNumber;
    }

    /**
     * Evicts the validator cache entry for the given document. Silently
     * skips when either part is missing or Redis is not configured on
     * this replica.
     */
    public void evict(String documentType, String documentNumber) {
        if (documentType == null || documentType.isBlank()
                || documentNumber == null || documentNumber.isBlank()) {
            return;
        }
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            log.debug("Redis unavailable — validator cache eviction skipped for {}/{}",
                    documentType, documentNumber);
            return;
        }
        String key = keyFor(documentType, documentNumber);
        try {
            Boolean removed = redis.delete(key);
            if (Boolean.TRUE.equals(removed)) {
                log.debug("Validator cache evicted: {}", key);
            }
        } catch (RuntimeException ex) {
            log.warn("Validator cache eviction failed for {}: {}", key, ex.getMessage());
        }
    }

    /**
     * Convenience: walks {@code membership → member → person} to resolve
     * the document, then evicts. Used by the membership lifecycle service,
     * the payments service, and the daily status sweep runner.
     *
     * <p>No-ops cleanly if any step of the chain is missing (e.g. the
     * membership was loaded with a {@code null} member, which would not
     * happen in normal code paths but defends against test fixtures).</p>
     */
    public void evictForMembership(Membership membership) {
        if (membership == null) return;
        Member member = membership.getMember();
        if (member == null) return;
        Person person = member.getPerson();
        if (person == null) return;
        evict(person.getDocumentType(), person.getDocumentNumber());
    }
}
