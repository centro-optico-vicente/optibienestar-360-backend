package com.fenixcore.optibienestar360.core.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.time.Duration;

/**
 * HTTP caching for the anonymous plan / ally / product catalog consumed by the public
 * landing ({@code centro-optico-vicente-web}), which fetches it client-side on
 * every visit.
 *
 * <p>The data changes with the database (allies get added regularly), so it is
 * cached <b>briefly and revalidated</b> rather than kept server-side:
 * {@link #publicCatalog()} lets browsers / proxies reuse a response for 60 s and
 * serve it stale for 5 more minutes while revalidating, so a new ally shows up
 * within about a minute. The ETag filter turns those revalidations into bodiless
 * 304s when nothing changed. Spring Security's default {@code no-cache} header
 * writer backs off once a controller sets {@code Cache-Control} itself.</p>
 *
 * <p>No {@code @Cacheable} here on purpose: the listings are keyed by page, size,
 * free-text {@code q} and filters, and every ally/plan write would need a
 * matching {@code @CacheEvict}.</p>
 */
@Configuration
public class PublicHttpCacheConfig {

    private static final Duration MAX_AGE = Duration.ofSeconds(60);
    private static final Duration STALE_WHILE_REVALIDATE = Duration.ofMinutes(5);

    /** {@code Cache-Control} for the public plan / ally endpoints. New instance per call ({@link CacheControl} is a mutable builder). */
    public static CacheControl publicCatalog() {
        return CacheControl.maxAge(MAX_AGE)
                .cachePublic()
                .staleWhileRevalidate(STALE_WHILE_REVALIDATE);
    }

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> publicCatalogEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        // Servlet prefix patterns: each also matches the bare collection path.
        registration.addUrlPatterns("/v1/public/plans/*", "/v1/public/allies/*",
                "/v1/public/products/*", "/v1/public/services/*");
        registration.setName("publicCatalogEtagFilter");
        return registration;
    }
}
