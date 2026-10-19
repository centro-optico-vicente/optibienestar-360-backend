package com.fenixcore.optibienestar360.core.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import com.fenixcore.optibienestar360.modules.membership.entity.PlanTypeConverter;
import com.fenixcore.optibienestar360.modules.terms.entity.TermTypeConverter;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round trip of the {@link CodedEnum}s: database ↔ enum ↔ JSON always uses the
 * Spanish code, never the English constant name.
 */
class CodedEnumTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TermTypeConverter termTypeConverter = new TermTypeConverter();
    private final PlanTypeConverter planTypeConverter = new PlanTypeConverter();

    @Test
    void termType_codesFollowTheGlossary() {
        assertThat(TermType.MEMBER.code()).isEqualTo("AFILIADO");
        assertThat(TermType.ADVISOR.code()).isEqualTo("ASESOR");
        assertThat(TermType.ALLY.code()).isEqualTo("ALIADO");
    }

    @Test
    void planType_codesStayInSpanish() {
        assertThat(PlanType.INDIVIDUAL.code()).isEqualTo("INDIVIDUAL");
        assertThat(PlanType.FAMILY.code()).isEqualTo("FAMILIAR");
        assertThat(PlanType.CORPORATE.code()).isEqualTo("CORPORATIVO");
    }

    @ParameterizedTest
    @EnumSource(TermType.class)
    void termType_roundTripsThroughDatabaseAndJson(TermType type) throws Exception {
        String column = termTypeConverter.convertToDatabaseColumn(type);
        assertThat(column).isEqualTo(type.code());
        assertThat(termTypeConverter.convertToEntityAttribute(column)).isEqualTo(type);

        String json = objectMapper.writeValueAsString(type);
        assertThat(json).isEqualTo("\"" + type.code() + "\"");
        assertThat(objectMapper.readValue(json, TermType.class)).isEqualTo(type);
    }

    @ParameterizedTest
    @EnumSource(PlanType.class)
    void planType_roundTripsThroughDatabaseAndJson(PlanType type) throws Exception {
        String column = planTypeConverter.convertToDatabaseColumn(type);
        assertThat(column).isEqualTo(type.code());
        assertThat(planTypeConverter.convertToEntityAttribute(column)).isEqualTo(type);

        String json = objectMapper.writeValueAsString(type);
        assertThat(json).isEqualTo("\"" + type.code() + "\"");
        assertThat(objectMapper.readValue(json, PlanType.class)).isEqualTo(type);
    }

    @Test
    void nullsPassThroughTheConverters() {
        assertThat(termTypeConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(termTypeConverter.convertToEntityAttribute(null)).isNull();
        assertThat(planTypeConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(planTypeConverter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unknownCodes_areRejected() {
        // Retired code (V185) and the Java constant names are not valid codes.
        assertThatThrownBy(() -> TermType.fromCode("PROMOTOR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TermType.fromCode("ADVISOR")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanType.fromCode("FAMILY")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> termTypeConverter.convertToEntityAttribute("PROMOTOR"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
