package com.fenixcore.optisaludplus.core.config;

import com.fenixcore.optisaludplus.core.web.UnpagedAwarePageableArgumentResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Wires the project-wide {@link UnpagedAwarePageableArgumentResolver} into Spring
 * MVC's argument-resolver chain so every controller that accepts a
 * {@link org.springframework.data.domain.Pageable} parameter supports the
 * {@code ?size=-1} / {@code ?unpaged=true} convention.
 *
 * <p>Spring Boot 4 split data autoconfig into per-area jars. The new
 * {@code DataWebAutoConfiguration} (in {@code spring-boot-data-commons}) exposes
 * a {@code PageableHandlerMethodArgumentResolverCustomizer} but NOT a
 * {@code PageableHandlerMethodArgumentResolver} bean — the resolver is created
 * and registered with MVC by {@code SpringDataWebConfiguration} (in
 * {@code spring-data-commons}) via its own {@code WebMvcConfigurer}. Defining a
 * user bean of that resolver type therefore does NOT replace anything; it makes
 * Spring Data back off, removes the default Pageable handling from the MVC
 * chain, and the request falls through to {@code ModelAttributeMethodProcessor},
 * which fails with {@code IllegalStateException: No primary or single unique
 * constructor found for interface Pageable}.</p>
 *
 * <p>Correct approach: register our resolver via {@code addArgumentResolvers}
 * and use {@link Order @Order(HIGHEST_PRECEDENCE)} so this configurer runs
 * before Spring Data's, which guarantees our resolver lands first in the chain
 * and {@code supportsParameter(Pageable)} returns true before Spring Data's
 * resolver gets a chance. Customizers from {@link PageableHandlerMethodArgumentResolverCustomizer}
 * (e.g. the one auto-wired by Boot from {@code spring.data.web.pageable.*}) are
 * applied to our instance too, so any project-wide property overrides keep
 * working.</p>
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaginationConfig implements WebMvcConfigurer {

    private final ObjectProvider<PageableHandlerMethodArgumentResolverCustomizer> customizers;

    public PaginationConfig(ObjectProvider<PageableHandlerMethodArgumentResolverCustomizer> customizers) {
        this.customizers = customizers;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        UnpagedAwarePageableArgumentResolver resolver = new UnpagedAwarePageableArgumentResolver();
        customizers.orderedStream().forEach(c -> c.customize(resolver));
        resolvers.add(resolver);
    }
}
