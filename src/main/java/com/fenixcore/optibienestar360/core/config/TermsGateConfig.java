package com.fenixcore.optibienestar360.core.config;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.service.TermsAcceptanceService;
import com.fenixcore.optibienestar360.modules.terms.web.TermsGateInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires {@link TermsGateInterceptor}: the advisor and ally self-service
 * endpoints stay closed until the caller accepts the contract of that kind.
 * Admin endpoints ({@code /v1/admin/**}) and {@code /v1/me/**} (including
 * {@code /v1/me/terms}) are never gated.
 */
@Configuration
@RequiredArgsConstructor
public class TermsGateConfig implements WebMvcConfigurer {

    private final TermsAcceptanceService termsAcceptanceService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TermsGateInterceptor(termsAcceptanceService, TermType.ADVISOR))
                .addPathPatterns("/v1/advisors/me", "/v1/advisors/me/**");
        registry.addInterceptor(new TermsGateInterceptor(termsAcceptanceService, TermType.ALLY))
                .addPathPatterns("/v1/allies/me", "/v1/allies/me/**");
    }
}
