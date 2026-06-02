package com.fenixcore.optisaludplus.core.config;

import com.fenixcore.optisaludplus.core.web.UnpagedAwarePageableArgumentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;

/**
 * Overrides Spring Boot's autoconfigured {@link PageableHandlerMethodArgumentResolver}
 * with our custom {@link UnpagedAwarePageableArgumentResolver}, so every controller
 * that accepts a {@link org.springframework.data.domain.Pageable} parameter supports
 * the project-wide {@code ?size=-1} / {@code ?unpaged=true} convention without
 * having to wire it case by case.
 *
 * <p>The autoconfig bean is {@code @ConditionalOnMissingBean}, so declaring our own
 * bean of the same type wins.</p>
 */
@Configuration
public class PaginationConfig {

    @Bean
    public PageableHandlerMethodArgumentResolver pageableResolver() {
        return new UnpagedAwarePageableArgumentResolver();
    }
}
