package com.fenixcore.optibienestar360.core.display;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.context.i18n.LocaleContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Emits the {@code _Display} siblings for every {@link Display}-annotated DTO
 * field at serialization time (hub ADR 0014), so DTOs stay lean records and
 * the presentational strings are resolved in one place from the request
 * {@code Locale}.
 *
 * <ul>
 *   <li>A {@link DisplayRef} (or {@code @Display(fk=...)}) field is replaced
 *   by the flat pair {@code <name>_Uuid} + {@code <name>_Display}; the ref
 *   object is not emitted.</li>
 *   <li>A scalar {@code @Display} field keeps its raw value and gains a
 *   {@code <name>_Display} string.</li>
 * </ul>
 *
 * Wired into the primary {@code ObjectMapper} in {@code JacksonConfig}.
 */
public class DisplayBeanSerializerModifier extends BeanSerializerModifier {

	private final transient DisplayFormatter formatter;

	public DisplayBeanSerializerModifier(DisplayFormatter formatter) {
		this.formatter = formatter;
	}

	@Override
	public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
													BeanDescription beanDesc,
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
			if (isForeignKey(display, writer)) {
				String rel = display.fk().isEmpty() ? writer.getName() : display.fk();
				result.add(new FkPairWriter(writer, rel, formatter));
			} else {
				result.add(writer);
				result.add(new ScalarDisplayWriter(writer, display, formatter));
			}
		}
		return touched ? result : beanProperties;
	}

	private static boolean isForeignKey(Display display, BeanPropertyWriter writer) {
		return !display.fk().isEmpty() || writer.getType().hasRawClass(DisplayRef.class);
	}

	// ─── Writers ───────────────────────────────────────────────────────────

	/**
	 * Replaces a {@link DisplayRef} field with the flat triple {@code <name>_Uuid}
	 * + {@code <name>_Display} + {@code <name>_Code}. The first two are always
	 * written (as a pair) unless nulls are suppressed; {@code <name>_Code} is
	 * emitted only when the ref carries a non-null natural key (catalogs, plan,
	 * person document number) — relations without a code (e.g. {@code city})
	 * simply omit it.
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
		public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
			DisplayRef ref = (DisplayRef) get(bean);
			if (ref == null && willSuppressNulls()) {
				return;
			}
			String base = getName();
			if (ref == null || ref.uuid() == null) {
				gen.writeNullField(base + "_Uuid");
			} else {
				gen.writeStringField(base + "_Uuid", ref.uuid().toString());
			}
			writeStringOrNull(gen, base + "_Display", formatter.fkLabel(rel, ref));
			if (ref != null && ref.code() != null) {
				gen.writeStringField(base + "_Code", ref.code());
			}
		}
	}

	/** Adds {@code <name>_Display} next to a scalar field (the raw value is kept by the base writer). */
	private static final class ScalarDisplayWriter extends BeanPropertyWriter {
		private final transient DisplayFormatter formatter;
		private final transient Display display;

		ScalarDisplayWriter(BeanPropertyWriter base, Display display, DisplayFormatter formatter) {
			super(base);
			this.display = display;
			this.formatter = formatter;
		}

		@Override
		public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
			Object raw = get(bean);
			if (raw == null && willSuppressNulls()) {
				return;
			}
			String currencyCode = resolveMoneyCurrency(bean, display);
			String value = scalarDisplay(formatter, raw, display, getName(), LocaleContextHolder.getLocale(), currencyCode);
			writeStringOrNull(gen, getName() + "_Display", value);
		}
	}

	private static void writeStringOrNull(JsonGenerator gen, String field, String value) throws java.io.IOException {
		if (value == null) {
			gen.writeNullField(field);
		} else {
			gen.writeStringField(field, value);
		}
	}

	// ─── Scalar dispatch ───────────────────────────────────────────────────

	static String scalarDisplay(DisplayFormatter formatter, Object value, Display display, String field, Locale locale) {
		return scalarDisplay(formatter, value, display, field, locale, null);
	}

	static String scalarDisplay(DisplayFormatter formatter, Object value, Display display, String field,
			Locale locale, String currencyCode) {
		if (value == null) {
			return null;
		}
		Display.Kind kind = display.value() == Display.Kind.AUTO ? infer(value) : display.value();
		return switch (kind) {
			case DATETIME -> formatter.dateTime(asInstant(value), locale);
			case DATE -> value instanceof LocalDate d ? formatter.date(d, locale) : null;
			case MONEY -> formatter.money(asBigDecimal(value), currencyCode, locale);
			case NUMBER -> value instanceof Number n ? formatter.number(n, locale) : null;
			case PERCENT -> value instanceof Number n ? formatter.number(n, locale) + "%" : null;
			case ENUM -> formatter.enumLabel(scope(display, field), String.valueOf(value), locale);
			case BOOLEAN -> value instanceof Boolean b ? formatter.bool(b, locale) : null;
			case AUTO -> null;
		};
	}

	/**
	 * Resolves the ISO currency code for a {@code @Display(MONEY)} field from
	 * its {@code moneyCurrencyField} sibling on the same bean (a zero-arg
	 * record-component accessor). {@code null} when unset, not {@code MONEY},
	 * or the reflective call fails for any reason — the formatter degrades to
	 * its own default rather than the writer throwing.
	 */
	static String resolveMoneyCurrency(Object bean, Display display) {
		if (display.value() != Display.Kind.MONEY || display.moneyCurrencyField().isEmpty()) {
			return null;
		}
		try {
			Object v = bean.getClass().getMethod(display.moneyCurrencyField()).invoke(bean);
			return v == null ? null : v.toString();
		} catch (ReflectiveOperationException unavailable) {
			return null;
		}
	}

	private static Display.Kind infer(Object value) {
		if (value instanceof Instant || value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
			return Display.Kind.DATETIME;
		}
		if (value instanceof LocalDate) {
			return Display.Kind.DATE;
		}
		if (value instanceof Boolean) {
			return Display.Kind.BOOLEAN;
		}
		if (value instanceof Number) {
			return Display.Kind.NUMBER;
		}
		return Display.Kind.ENUM;
	}

	private static Instant asInstant(Object value) {
		if (value instanceof Instant i) {
			return i;
		}
		if (value instanceof OffsetDateTime o) {
			return o.toInstant();
		}
		if (value instanceof ZonedDateTime z) {
			return z.toInstant();
		}
		return null;
	}

	private static BigDecimal asBigDecimal(Object value) {
		if (value instanceof BigDecimal b) {
			return b;
		}
		if (value instanceof Number n) {
			return BigDecimal.valueOf(n.doubleValue());
		}
		return null;
	}

	private static String scope(Display display, String field) {
		return display.enumScope().isEmpty() ? field : display.enumScope();
	}

}
