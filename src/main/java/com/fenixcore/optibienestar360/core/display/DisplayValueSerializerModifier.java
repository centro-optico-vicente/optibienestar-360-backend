package com.fenixcore.optibienestar360.core.display;

import org.springframework.context.i18n.LocaleContextHolder;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Jackson 3 port of {@link DisplayBeanSerializerModifier} — this is the one
 * that actually runs for Spring MVC JSON responses (Spring Boot 4 serializes
 * HTTP with Jackson 3 / {@code tools.jackson}; the Jackson 2 variant only
 * covers direct {@code com.fasterxml} {@code ObjectMapper} use). Same
 * contract: a {@code @Display} FK field becomes {@code <name>_Uuid} +
 * {@code <name>_Display}; a {@code @Display} scalar keeps its raw value and
 * gains {@code <name>_Display}. Registered in {@code JacksonConfig}.
 */
public class DisplayValueSerializerModifier extends ValueSerializerModifier {

	private final transient DisplayFormatter formatter;

	public DisplayValueSerializerModifier(DisplayFormatter formatter) {
		this.formatter = formatter;
	}

	@Override
	public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
													BeanDescription.Supplier beanDescSupplier,
													List<BeanPropertyWriter> beanProperties) {
		boolean touched = false;
		List<BeanPropertyWriter> result = new ArrayList<>(beanProperties.size() + 4);
		for (BeanPropertyWriter writer : beanProperties) {
			Display display = writer.getAnnotation(Display.class);
			if (display == null) {
				result.add(writer);
				continue;
			}
			touched = true;
			if (!display.fk().isEmpty() || writer.getType().hasRawClass(DisplayRef.class)) {
				String rel = display.fk().isEmpty() ? writer.getName() : display.fk();
				result.add(new FkPairWriter(writer, rel, formatter));
			} else {
				result.add(writer);
				result.add(new ScalarDisplayWriter(writer, display, formatter));
			}
		}
		return touched ? result : beanProperties;
	}

	private static void writeStringOrNull(JsonGenerator gen, String field, String value) {
		if (value == null) {
			gen.writeNullProperty(field);
		} else {
			gen.writeStringProperty(field, value);
		}
	}

	/**
	 * Replaces a {@link DisplayRef} field with the flat triple {@code <name>_Uuid}
	 * + {@code <name>_Display} + {@code <name>_Code} — see the Jackson 2 twin in
	 * {@link DisplayBeanSerializerModifier}. {@code _Code} is emitted only when
	 * the ref carries a non-null natural key.
	 */
	private static final class FkPairWriter extends BeanPropertyWriter {
		private final transient DisplayFormatter formatter;
		private final String rel;

		FkPairWriter(BeanPropertyWriter base, String rel, DisplayFormatter formatter) {
			super(base);
			this.rel = rel;
			this.formatter = formatter;
		}

		@Override
		public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext ctxt) throws Exception {
			DisplayRef ref = (DisplayRef) get(bean);
			if (ref == null && willSuppressNulls()) {
				return;
			}
			String base = getName();
			if (ref == null || ref.uuid() == null) {
				gen.writeNullProperty(base + "_Uuid");
			} else {
				gen.writeStringProperty(base + "_Uuid", ref.uuid().toString());
			}
			writeStringOrNull(gen, base + "_Display", formatter.fkLabel(rel, ref));
			if (ref != null && ref.code() != null) {
				gen.writeStringProperty(base + "_Code", ref.code());
			}
		}
	}

	/** Adds {@code <name>_Display} next to a scalar field (the base writer keeps the raw value). */
	private static final class ScalarDisplayWriter extends BeanPropertyWriter {
		private final transient DisplayFormatter formatter;
		private final transient Display display;

		ScalarDisplayWriter(BeanPropertyWriter base, Display display, DisplayFormatter formatter) {
			super(base);
			this.display = display;
			this.formatter = formatter;
		}

		@Override
		public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext ctxt) throws Exception {
			Object raw = get(bean);
			if (raw == null && willSuppressNulls()) {
				return;
			}
			String value = DisplayBeanSerializerModifier.scalarDisplay(
					formatter, raw, display, getName(), LocaleContextHolder.getLocale());
			writeStringOrNull(gen, getName() + "_Display", value);
		}
	}

}
