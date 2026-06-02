package com.fenixcore.optisaludplus.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
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
        // Configured origins are split: entries containing "*" are routed to
        // allowedOriginPatterns (e.g. "*" for any origin, or "https://*.centroopticovicente.com"
        // for any subdomain), the rest to exact allowedOrigins. The CORS spec forbids wildcards
        // in plain allowedOrigins when allowCredentials(true), but permits them in
        // allowedOriginPatterns — Spring echoes the matched origin back — so credentials stay on.
        List<String> exactOrigins = allowedOrigins.stream()
            .filter(origin -> !origin.contains("*"))
            .toList();
        List<String> patternOrigins = new ArrayList<>(allowedOrigins.stream()
            .filter(origin -> origin.contains("*"))
            .toList());

        if (allowLocalhost) {
            patternOrigins.addAll(List.of(LOCALHOST_ORIGIN_PATTERNS));
        }

        CorsRegistration mapping = registry.addMapping("/v1/**")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With", "Cache-Control")
            .exposedHeaders("Authorization")
            .allowCredentials(true)
            .maxAge(3600)
        ;

        if (!exactOrigins.isEmpty()) {
            mapping.allowedOrigins(exactOrigins.toArray(String[]::new));
        }
        if (!patternOrigins.isEmpty()) {
            mapping.allowedOriginPatterns(patternOrigins.toArray(String[]::new));
        }
    }
}
