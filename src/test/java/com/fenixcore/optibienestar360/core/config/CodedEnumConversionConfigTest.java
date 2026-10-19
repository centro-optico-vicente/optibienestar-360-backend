package com.fenixcore.optibienestar360.core.config;

import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import io.github.perplexhub.rsql.RSQLJPASupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.format.support.DefaultFormattingConversionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Spanish codes bind to the English constants in both conversion services
 * that read request input: Spring MVC (path variables / params) and rsql-jpa
 * (the {@code filter} query param, e.g. {@code type==FAMILIAR}).
 */
class CodedEnumConversionConfigTest {

    private final CodedEnumConversionConfig config = new CodedEnumConversionConfig();

    @Test
    void mvcConversion_acceptsTheCodes() {
        DefaultFormattingConversionService service = new DefaultFormattingConversionService();
        config.addFormatters(service);

        assertThat(service.convert("ASESOR", TermType.class)).isEqualTo(TermType.ADVISOR);
        assertThat(service.convert("AFILIADO", TermType.class)).isEqualTo(TermType.MEMBER);
        assertThat(service.convert("FAMILIAR", PlanType.class)).isEqualTo(PlanType.FAMILY);
        assertThatThrownBy(() -> service.convert("ADVISOR", TermType.class))
                .isInstanceOf(ConversionFailedException.class);
    }

    @Test
    void rsqlConversion_acceptsTheCodes() {
        // Instantiating the config (field above) ran its static registration.
        assertThat(RSQLJPASupport.getConversionService().convert("FAMILIAR", PlanType.class))
                .isEqualTo(PlanType.FAMILY);
        assertThat(RSQLJPASupport.getConversionService().convert("CORPORATIVO", PlanType.class))
                .isEqualTo(PlanType.CORPORATE);
        assertThat(RSQLJPASupport.getConversionService().convert("ALIADO", TermType.class))
                .isEqualTo(TermType.ALLY);
    }
}
