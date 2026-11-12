package com.fenixcore.optibienestar360.modules.terms.web;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache semantics of {@link TermsGateCheck} with caching enabled (as in prod):
 * "nothing pending" is reused, "pending" is always re-evaluated.
 */
class TermsGateCheckTest {

    @Configuration
    @EnableCaching
    static class Config {
        static final TermsAcceptanceService SERVICE = mock(TermsAcceptanceService.class);

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(TermsGateCheck.CACHE);
        }

        @Bean
        TermsGateCheck termsGateCheck() {
            return new TermsGateCheck(SERVICE);
        }
    }

    private AnnotationConfigApplicationContext context;
    private TermsGateCheck check;
    private final TermsAcceptanceService service = Config.SERVICE;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(service);
        context = new AnnotationConfigApplicationContext(Config.class);
        check = context.getBean(TermsGateCheck.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void nothingPendingIsCachedPerUserAndTermType() {
        UUID user = UUID.randomUUID();
        when(service.hasPending(user, TermType.ADVISOR)).thenReturn(false);
        when(service.hasPending(user, TermType.ALLY)).thenReturn(false);

        assertThat(check.hasPending(user, TermType.ADVISOR)).isFalse();
        assertThat(check.hasPending(user, TermType.ADVISOR)).isFalse();
        assertThat(check.hasPending(user, TermType.ALLY)).isFalse();

        verify(service, times(1)).hasPending(user, TermType.ADVISOR);
        verify(service, times(1)).hasPending(user, TermType.ALLY);
    }

    @Test
    void pendingIsNeverCachedSoAcceptingTakesEffectOnTheNextRequest() {
        UUID user = UUID.randomUUID();
        when(service.hasPending(user, TermType.ADVISOR)).thenReturn(true, true, false);

        assertThat(check.hasPending(user, TermType.ADVISOR)).isTrue();
        assertThat(check.hasPending(user, TermType.ADVISOR)).isTrue();
        // the user accepts the terms here
        assertThat(check.hasPending(user, TermType.ADVISOR)).isFalse();

        verify(service, times(3)).hasPending(user, TermType.ADVISOR);
    }
}
