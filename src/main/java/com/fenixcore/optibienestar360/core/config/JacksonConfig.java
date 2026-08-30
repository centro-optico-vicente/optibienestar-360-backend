package com.fenixcore.optibienestar360.core.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fenixcore.optibienestar360.core.display.DisplayBeanSerializerModifier;
import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    /**
     * The primary mapper used by the web layer. Registers the
     * {@code _Display} sibling emitter (ADR 0014) so every
     * {@code @Display}-annotated DTO field is serialized with its
     * locale-resolved companion.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(DisplayFormatter displayFormatter) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        SimpleModule displayModule = new SimpleModule("DisplayModule");
        displayModule.setSerializerModifier(new DisplayBeanSerializerModifier(displayFormatter));
        mapper.registerModule(displayModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        mapper.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        return mapper;
    }
}
