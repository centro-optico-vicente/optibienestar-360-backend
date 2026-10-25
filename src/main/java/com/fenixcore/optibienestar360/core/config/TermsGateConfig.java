package com.fenixcore.optibienestar360.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.modules.terms.web.TermsGateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires {@link TermsGateInterceptor}: the advisor and ally self-service
 * endpoints stay closed until the caller accepts the contract of that kind.
 * Each portal is reachable under several aliases (primary {@code /v1/me/...},
 * {@code /v1/advisors/me}, {@code /v1/allies/me}, and the legacy
 * {@code /v1/promoter/me}, {@code /v1/ally}, {@code /v1/aliado}); every one is
 * gated so the lock can't be skipped by switching alias. Admin endpoints
 * ({@code /v1/admin/**}) and {@code /v1/me/terms} are never gated.
 *
 * <p>Tests: the gate queries the database (pending contracts), so an MVC IT that
 * calls a portal route and mocks its services must also mock
 * {@code TermsAcceptanceService} ({@code hasPending} → {@code false}).</p>
 */
@Configuration
@RequiredArgsConstructor
public class TermsGateConfig implements WebMvcConfigurer {

    private final TermsAcceptanceService termsAcceptanceService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    @Value("${problems.base-url}")
    private String problemsBase;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gate(TermType.ADVISOR))
                .addPathPatterns(
                        "/v1/advisors/me", "/v1/advisors/me/**",
                        "/v1/me/advisor", "/v1/me/advisor/**",
                        "/v1/promoter/me", "/v1/promoter/me/**");
        registry.addInterceptor(gate(TermType.ALLY))
                .addPathPatterns(
                        "/v1/allies/me", "/v1/allies/me/**",
                        "/v1/me/allies", "/v1/me/allies/**",
                        "/v1/ally/**", "/v1/aliado/**");
    }

    private TermsGateInterceptor gate(TermType type) {
        return new TermsGateInterceptor(termsAcceptanceService, type, messageSource, objectMapper, problemsBase);
    }
}
