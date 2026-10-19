package com.fenixcore.optibienestar360.core.config;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import io.github.perplexhub.rsql.RSQLJPASupport;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes every {@link com.fenixcore.optibienestar360.core.entity.CodedEnum} accept
 * its Spanish code (not the Java constant name) outside JSON bodies:
 * <ul>
 *   <li>Spring MVC path variables and request params — e.g.
 *       {@code GET /v1/public/terms/ASESOR} binds {@link TermType#ADVISOR};</li>
 *   <li>RSQL filters — e.g. {@code filter=type==FAMILIAR} binds {@link PlanType#FAMILY}.
 *       Without this, rsql-jpa falls back to {@code Enum.valueOf} and rejects the code.</li>
 * </ul>
 * A converter registered for the concrete enum type wins over Spring's generic
 * String→Enum factory in both conversion services.
 */
@Configuration
public class CodedEnumConversionConfig implements WebMvcConfigurer {

    static {
        // rsql-jpa keeps its own static ConversionService (RSQLCommonSupport).
        RSQLJPASupport.addConverter(PlanType.class, PlanType::fromCode);
        RSQLJPASupport.addConverter(TermType.class, TermType::fromCode);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, TermType.class, TermType::fromCode);
        registry.addConverter(String.class, PlanType.class, PlanType::fromCode);
    }
}
