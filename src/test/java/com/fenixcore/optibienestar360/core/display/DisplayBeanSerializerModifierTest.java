package com.fenixcore.optibienestar360.core.display;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@code @Display} → {@code _Display} emission contract (ADR 0014)
 * against the real message bundles, without a Spring context.
 */
class DisplayBeanSerializerModifierTest {

	private record Sample(
			UUID uuid,
			@Display(fk = "allyType") DisplayRef allyType,
			@Display DisplayRef city,
			@Display(Display.Kind.DATETIME) Instant createdAt,
			@Display(Display.Kind.BOOLEAN) boolean active,
			@Display(Display.Kind.ENUM) String status,
			@Display(Display.Kind.MONEY) BigDecimal amount
	) {}

	private final ObjectMapper mapper = buildMapper();

	private static ObjectMapper buildMapper() {
		ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
		ms.setBasename("messages");
		ms.setDefaultEncoding("UTF-8");
		DisplayFormatter formatter = new DisplayFormatter(ms);
		ObjectMapper m = new ObjectMapper();
		m.registerModule(new JavaTimeModule());
		m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		SimpleModule module = new SimpleModule("DisplayModule");
		module.setSerializerModifier(new DisplayBeanSerializerModifier(formatter));
		m.registerModule(module);
		return m;
	}

	@AfterEach
	void resetLocale() {
		LocaleContextHolder.resetLocaleContext();
	}

	@Test
	void emitsFlatFkPairAndScalarSiblings_es() throws Exception {
		LocaleContextHolder.setLocale(new Locale("es"));
		UUID typeUuid = UUID.randomUUID();
		Sample dto = new Sample(
				UUID.randomUUID(),
				new DisplayRef(typeUuid, "OPT", "Óptica"),
				new DisplayRef(UUID.randomUUID(), null, "Maracaibo"),
				Instant.parse("2026-03-14T13:22:05Z"),
				true,
				"ACTIVE",
				new BigDecimal("1200.00"));

		JsonNode json = mapper.valueToTree(dto);

		// FK: flat triple, no nested object
		assertThat(json.has("allyType")).isFalse();
		assertThat(json.get("allyType_Uuid").asText()).isEqualTo(typeUuid.toString());
		assertThat(json.get("allyType_Display").asText()).isEqualTo("OPT - Óptica");
		assertThat(json.get("allyType_Code").asText()).isEqualTo("OPT");
		assertThat(json.get("city_Uuid").isNull()).isFalse();
		assertThat(json.get("city_Display").asText()).isEqualTo("Maracaibo");
		// city has no natural key → _Code is omitted (not written as null)
		assertThat(json.has("city_Code")).isFalse();

		// Scalars: raw kept + _Display sibling
		assertThat(json.get("createdAt").asText()).startsWith("2026-03-14T13:22:05");
		assertThat(json.get("createdAt_Display").asText()).isEqualTo("14-03-2026 09:22");
		assertThat(json.get("active").asBoolean()).isTrue();
		assertThat(json.get("active_Display").asText()).isEqualTo("Sí");
		assertThat(json.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(json.get("status_Display").asText()).isEqualTo("Activo");
		assertThat(json.get("amount_Display").asText()).contains("1.200,00");
	}

	@Test
	void nullForeignKeyYieldsBothNull() throws Exception {
		LocaleContextHolder.setLocale(new Locale("es"));
		Sample dto = new Sample(UUID.randomUUID(), null, null,
				null, false, null, null);

		JsonNode json = mapper.valueToTree(dto);

		assertThat(json.get("allyType_Uuid").isNull()).isTrue();
		assertThat(json.get("allyType_Display").isNull()).isTrue();
		assertThat(json.has("allyType_Code")).isFalse();
		assertThat(json.get("createdAt_Display").isNull()).isTrue();
		assertThat(json.get("active_Display").asText()).isEqualTo("No");
		assertThat(json.get("status_Display").isNull()).isTrue();
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record NonNullSample(
			UUID uuid,
			@Display(fk = "person") DisplayRef person,
			@Display(Display.Kind.MONEY) java.math.BigDecimal amount
	) {}

	@Test
	void nonNullDtoSuppressesNullDisplaySiblings() throws Exception {
		LocaleContextHolder.setLocale(new Locale("es"));
		JsonNode json = mapper.valueToTree(new NonNullSample(UUID.randomUUID(), null, null));

		assertThat(json.has("person_Uuid")).isFalse();
		assertThat(json.has("person_Display")).isFalse();
		assertThat(json.has("person_Code")).isFalse();
		assertThat(json.has("amount_Display")).isFalse();
	}

	@Test
	void personShapedFkEmitsDocumentNumberAsCode() throws Exception {
		LocaleContextHolder.setLocale(new Locale("es"));
		UUID personUuid = UUID.randomUUID();
		JsonNode json = mapper.valueToTree(new NonNullSample(
				UUID.randomUUID(),
				new DisplayRef(personUuid, "V-12345678", "Juan Pérez"),
				null));

		assertThat(json.get("person_Uuid").asText()).isEqualTo(personUuid.toString());
		assertThat(json.get("person_Display").asText()).isEqualTo("V-12345678 Juan Pérez");
		assertThat(json.get("person_Code").asText()).isEqualTo("V-12345678");
	}

	@Test
	void scalarLabelsFollowLocale_en() throws Exception {
		LocaleContextHolder.setLocale(Locale.ENGLISH);
		Sample dto = new Sample(
			UUID.randomUUID(),
				new DisplayRef(UUID.randomUUID(), "OPT", "Óptica"),
				null,
				Instant.parse("2026-03-14T13:22:05Z"),
				true,
				"ACTIVE",
				null
		);

		JsonNode json = mapper.valueToTree(dto);

		assertThat(json.get("active_Display").asText()).isEqualTo("Yes");
		assertThat(json.get("status_Display").asText()).isEqualTo("Active");
		assertThat(json.get("createdAt_Display").asText()).isEqualTo("03-14-2026 09:22");
		// catalog label + code are data, not translated
		assertThat(json.get("allyType_Display").asText()).isEqualTo("OPT - Óptica");
		assertThat(json.get("allyType_Code").asText()).isEqualTo("OPT");
	}

}
