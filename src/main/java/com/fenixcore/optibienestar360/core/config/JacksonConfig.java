package com.fenixcore.optibienestar360.core.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fenixcore.optibienestar360.core.display.DisplayBeanSerializerModifier;
import com.fenixcore.optibienestar360.core.display.DisplayFormatter;
import com.fenixcore.optibienestar360.core.display.DisplayValueSerializerModifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

	/**
	 * Jackson 3 module carrying the {@code _Display} sibling emitter
	 * (hub ADR 0014). This is the one that reaches Spring MVC responses —
	 * Spring Boot 4 serializes HTTP with Jackson 3 ({@code tools.jackson});
	 * {@code JacksonModule} beans are auto-registered on that mapper.
	 */
	@Bean
	public tools.jackson.databind.module.SimpleModule displayJackson3Module(DisplayFormatter displayFormatter) {
		tools.jackson.databind.module.SimpleModule module =
				new tools.jackson.databind.module.SimpleModule("DisplayModule3");
		module.setSerializerModifier(new DisplayValueSerializerModifier(displayFormatter));
		return module;
	}

	/**
	 * Primary Jackson 2 mapper for direct {@code com.fasterxml}
	 * {@code ObjectMapper} use in code. Carries the Jackson 2 variant of the
	 * {@code _Display} emitter for parity.
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
