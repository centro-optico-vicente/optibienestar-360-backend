package com.fenixcore.optisaludplus.modules.validator.service;

import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the validator cache evictor. Uses a hand-rolled
 * ObjectProvider so the same suite exercises the happy path (Redis
 * available) and the graceful-degradation path (Redis missing) without
 * Spring context.
 */
class ValidatorCacheServiceTest {

    // ─── Redis available ────────────────────────────────────────────────────

    @Test
    void evict_calls_redis_delete_with_canonical_key() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.delete(anyString())).thenReturn(true);
        ValidatorCacheService service = new ValidatorCacheService(provider(redis));

        service.evict("V", "12345678");

        verify(redis).delete("validator:document:V:12345678");
    }

    @Test
    void evictForMembership_walks_the_chain_and_evicts() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.delete(anyString())).thenReturn(true);
        ValidatorCacheService service = new ValidatorCacheService(provider(redis));

        service.evictForMembership(membershipWith("V", "87654321"));

        verify(redis).delete("validator:document:V:87654321");
    }

    @Test
    void evict_swallows_redis_exception() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.delete(anyString())).thenThrow(new RuntimeException("redis down"));
        ValidatorCacheService service = new ValidatorCacheService(provider(redis));

        // No exception bubbled — caller never breaks from a Redis hiccup.
        service.evict("V", "12345678");
        verify(redis, atLeastOnce()).delete(anyString());
    }

    // ─── Redis unavailable ──────────────────────────────────────────────────

    @Test
    void evict_noOps_when_redis_provider_empty() {
        ValidatorCacheService service = new ValidatorCacheService(emptyProvider());
        // No throw, no NPE — the method just returns.
        service.evict("V", "12345678");
    }

    // ─── Defensive paths ────────────────────────────────────────────────────

    @Test
    void evict_skips_blank_or_null_document_parts() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValidatorCacheService service = new ValidatorCacheService(provider(redis));

        service.evict(null, "12345678");
        service.evict("V", null);
        service.evict("", "12345678");
        service.evict("V", "");

        verify(redis, never()).delete(anyString());
    }

    @Test
    void evictForMembership_noOps_for_broken_chain() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValidatorCacheService service = new ValidatorCacheService(provider(redis));

        // null membership
        service.evictForMembership(null);
        // membership without member
        Membership m1 = new Membership();
        service.evictForMembership(m1);
        // member without person
        Membership m2 = new Membership();
        m2.setMember(new Member());
        service.evictForMembership(m2);

        verify(redis, never()).delete(anyString());
    }

    @Test
    void keyFor_returns_canonical_shape() {
        ValidatorCacheService service = new ValidatorCacheService(emptyProvider());
        assertThat(service.keyFor("V", "12345678")).isEqualTo("validator:document:V:12345678");
        assertThat(service.keyFor("E", "abc")).isEqualTo("validator:document:E:abc");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static Membership membershipWith(String documentType, String documentNumber) {
        Person person = new Person();
        person.setDocumentType(documentType);
        person.setDocumentNumber(documentNumber);
        Member member = new Member();
        member.setPerson(person);
        Membership membership = new Membership();
        membership.setUuid(UUID.randomUUID());
        membership.setMember(member);
        return membership;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate value) {
        ObjectProvider<StringRedisTemplate> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(value);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StringRedisTemplate> emptyProvider() {
        ObjectProvider<StringRedisTemplate> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(null);
        return p;
    }
}
