package com.fenixcore.optisaludplus.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "BearerAuth";

    /** API contract (OpenAPI spec) version — bumped manually on breaking contract changes. */
    private static final String API_CONTRACT_VERSION = "1.0.0";

    /**
     * Build/deploy version of the running backend. Comes from the VERSION env var (set by the
     * Dockerfile from the release tag, e.g. 0.0.017); falls back to dev-0.0.1 on local runs.
     * Independent of the API contract version above.
     */
    @Value("${app.version:dev-0.0.1}")
    private String buildVersion;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("OptiSalud Plus API")
                        .description("API REST — Sistema de membresías OptiSalud Plus / Centro Óptico Vicente"
                                + "\n\n**Build del backend:** `" + buildVersion + "`")
                        .version(API_CONTRACT_VERSION)
                        .contact(new Contact()
                                .name("Fenix Core")
                                .email("dev@fenixcore.com")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Access token JWT — obtener en POST /v1/auth/login")));
    }
}
