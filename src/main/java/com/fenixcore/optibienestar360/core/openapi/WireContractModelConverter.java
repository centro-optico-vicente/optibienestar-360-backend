package com.fenixcore.optibienestar360.core.openapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Makes the generated OpenAPI schema of response records match the JSON the
 * server really writes (ADR LOC-0015). springdoc builds schemas with
 * swagger-core's own Jackson 2 mapper, which never sees the serializer
 * modifier that Spring MVC uses for responses, so two things are lost and
 * restored here, globally and without per-DTO {@code @Schema} annotations:
 *
 * <ol>
 *   <li><b>{@code _Display} pairs</b> (hub ADR 0014, emitted at runtime by
 *   {@code DisplayValueSerializerModifier}): a {@link Display} foreign key
 *   ({@link DisplayRef} or {@code fk} set) is replaced by {@code <rel>_Uuid},
 *   {@code <rel>_Display} and the optional {@code <rel>_Code}; a {@link Display}
 *   scalar keeps its property and gains {@code <field>_Display}.</li>
 *   <li><b>{@code required} and nullability</b>: a primitive or a
 *   {@code @NotNull}/{@code @NotBlank}/{@code @NotEmpty} component is always
 *   present and non-null. Any other component is {@code required} and nullable
 *   because Jackson writes an explicit {@code null}, unless the record (or the
 *   component) is {@code @JsonInclude(NON_NULL/NON_EMPTY/NON_ABSENT/NON_DEFAULT)}:
 *   then the key is simply absent when empty, so it is optional and not
 *   nullable.</li>
 * </ol>
 *
 * <p>Scope: record schemas of this project whose name does not end in
 * {@code Request}/{@code Command} (requests keep what Bean Validation gives).
 * Idempotent, because the global swagger-core registry can invoke it more than
 * once for the same type.</p>
 */
@Component
public class WireContractModelConverter implements ModelConverter {

	private static final String BASE_PACKAGE = "com.fenixcore.optibienestar360.";
	private static final String REF_PREFIX = "#/components/schemas/";

	@Override
	@SuppressWarnings("rawtypes")
	public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
		if (!chain.hasNext()) {
			return null;
		}
		Schema resolved = chain.next().resolve(type, context, chain);
		Class<?> raw = rawClass(type.getType());
		if (resolved == null || raw == null || !isResponseRecord(raw)) {
			return resolved;
		}
		Schema model = resolved;
		if (model.getProperties() == null && model.get$ref() != null && model.get$ref().startsWith(REF_PREFIX)) {
			model = context.getDefinedModels().get(model.get$ref().substring(REF_PREFIX.length()));
		}
		if (model != null && model.getProperties() != null) {
			enrich(raw, model);
		}
		return resolved;
	}

	static boolean isResponseRecord(Class<?> type) {
		String name = type.getSimpleName();
		return type.isRecord()
				&& type.getName().startsWith(BASE_PACKAGE)
				&& !name.endsWith("Request")
				&& !name.endsWith("Command");
	}

	private static Class<?> rawClass(Type type) {
		if (type instanceof Class<?> c) {
			return c;
		}
		if (type instanceof JavaType jt) {
			return jt.getRawClass();
		}
		if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
			return c;
		}
		return null;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void enrich(Class<?> rec, Schema model) {
		boolean v31 = model.getSpecVersion() == SpecVersion.V31;
		Map<String, RecordComponent> components = new HashMap<>();
		for (RecordComponent c : rec.getRecordComponents()) {
			components.put(jsonName(rec, c), c);
		}
		boolean classOmitsNull = omitsNull(rec.getAnnotation(JsonInclude.class));

		Map<String, Schema> original = model.getProperties();
		Map<String, Schema> out = new LinkedHashMap<>();
		List<String> required = new ArrayList<>(model.getRequired() == null ? List.of() : model.getRequired());

		for (Map.Entry<String, Schema> entry : original.entrySet()) {
			String name = entry.getKey();
			Schema property = entry.getValue();
			RecordComponent component = components.get(name);
			if (component == null) {
				out.put(name, property);
				continue;
			}
			JsonInclude componentInclude = annotation(rec, component, JsonInclude.class);
			boolean omitNull = componentInclude != null ? omitsNull(componentInclude) : classOmitsNull;
			boolean primitive = component.getType().isPrimitive();
			boolean nonNull = primitive || hasNotNull(rec, component);
			// Present in the JSON whenever the value can be non-null, or nulls are written explicitly.
			boolean present = nonNull || !omitNull;

			Display display = annotation(rec, component, Display.class);
			boolean fk = display != null && (!display.fk().isEmpty() || component.getType() == DisplayRef.class);

			if (fk) {
				required.remove(name); // swagger may have listed the replaced property
				put(out, required, name + "_Uuid", string(v31, "uuid"), present, true);
				put(out, required, name + "_Display", string(v31, null), present, true);
				put(out, required, name + "_Code", string(v31, null), false, false);
				continue;
			}

			boolean nullable = !nonNull;
			put(out, required, name, property, present, present && nullable);
			if (display != null) {
				put(out, required, name + "_Display", string(v31, null), present, !primitive);
			}
		}
		model.setProperties(out);
		model.setRequired(required.isEmpty() ? null : required);
	}

	/** Adds (or re-marks, when re-run) one property with its required/nullable state. */
	@SuppressWarnings("rawtypes")
	private static void put(Map<String, Schema> out, List<String> required, String name,
							Schema schema, boolean isRequired, boolean nullable) {
		Schema target = out.getOrDefault(name, schema);
		if (nullable) {
			target = markNullable(target);
		}
		out.put(name, target);
		if (isRequired && !required.contains(name)) {
			required.add(name);
		}
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Schema markNullable(Schema property) {
		boolean v31 = property.getSpecVersion() == SpecVersion.V31;
		if (!v31) {
			if (property.get$ref() != null) {
				Schema wrapper = new Schema();
				wrapper.setNullable(true);
				wrapper.addAllOfItem(property);
				return wrapper;
			}
			property.setNullable(true);
			return property;
		}
		if (property.get$ref() != null) {
			JsonSchema wrapper = new JsonSchema();
			wrapper.addAnyOfItem(property);
			JsonSchema none = new JsonSchema();
			none.addType("null");
			wrapper.addAnyOfItem(none);
			return wrapper;
		}
		List<Schema> anyOf = property.getAnyOf();
		if (anyOf != null && anyOf.stream().anyMatch(s -> s.getTypes() != null && s.getTypes().contains("null"))) {
			return property;
		}
		Set<String> types = new LinkedHashSet<>();
		if (property.getTypes() != null) {
			types.addAll(property.getTypes());
		} else if (property.getType() != null) {
			types.add(property.getType());
		}
		if (types.isEmpty()) {
			return property; // schema without a type already admits null
		}
		types.add("null");
		property.setTypes(types);
		List enumValues = property.getEnum();
		if (enumValues != null && !enumValues.contains(null)) {
			property.addEnumItemObject(null);
		}
		return property;
	}

	@SuppressWarnings("rawtypes")
	private static Schema string(boolean v31, String format) {
		Schema schema;
		if (v31) {
			schema = new JsonSchema();
			schema.addType("string");
		} else {
			schema = new StringSchema();
		}
		if (format != null) {
			schema.setFormat(format);
		}
		return schema;
	}

	private static boolean omitsNull(JsonInclude include) {
		if (include == null) {
			return false;
		}
		return switch (include.value()) {
			case NON_NULL, NON_ABSENT, NON_EMPTY, NON_DEFAULT -> true;
			default -> false;
		};
	}

	private static boolean hasNotNull(Class<?> rec, RecordComponent c) {
		return annotation(rec, c, NotNull.class) != null
				|| annotation(rec, c, NotBlank.class) != null
				|| annotation(rec, c, NotEmpty.class) != null;
	}

	private static String jsonName(Class<?> rec, RecordComponent c) {
		JsonProperty property = annotation(rec, c, JsonProperty.class);
		return property != null && !property.value().isEmpty() ? property.value() : c.getName();
	}

	/**
	 * Record-component annotations are propagated only to the targets they
	 * declare, so look at the accessor, the backing field and the component.
	 */
	private static <A extends Annotation> A annotation(Class<?> rec, RecordComponent c, Class<A> type) {
		A found = c.getAccessor().getAnnotation(type);
		if (found != null) {
			return found;
		}
		try {
			found = rec.getDeclaredField(c.getName()).getAnnotation(type);
		} catch (NoSuchFieldException ignored) {
			// A record always has the backing field; kept for safety.
		}
		return found != null ? found : c.getAnnotation(type);
	}

}
