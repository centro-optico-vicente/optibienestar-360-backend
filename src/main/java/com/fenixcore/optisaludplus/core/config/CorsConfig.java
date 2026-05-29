package com.fenixcore.optisaludplus.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // localhost / 127.0.0.1 on any port — used as origin PATTERNS, not plain origins:
    // allowCredentials(true) forbids wildcards in allowedOrigins, but permits them here.
    private static final String[] LOCALHOST_ORIGIN_PATTERNS = {
        "http://localhost:[*]",
        "https://localhost:[*]",
        "http://127.0.0.1:[*]",
        "https://127.0.0.1:[*]"
    };

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:4000}")
    private List<String> allowedOrigins;

    @Value("${cors.allow-localhost:false}")
    private boolean allowLocalhost;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsRegistration mapping = registry.addMapping("/v1/**")
            .allowedOrigins(allowedOrigins.toArray(String[]::new))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With", "Cache-Control")
            .exposedHeaders("Authorization")
            .allowCredentials(true)
            .maxAge(3600)
        ;

        if (allowLocalhost) {
            mapping.allowedOriginPatterns(LOCALHOST_ORIGIN_PATTERNS);
        }
    }
}
