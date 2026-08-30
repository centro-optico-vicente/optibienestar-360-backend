package com.fenixcore.optibienestar360.core.display;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a DTO field that must travel with a read-only {@code _Display}
 * sibling resolved server-side by {@link DisplayFormatter} from the request
 * {@code Locale} (hub ADR 0014).
 *
 * <p>Two shapes:</p>
 * <ul>
 *   <li><b>Foreign key</b> — the field is a {@link DisplayRef} (or carries a
 *   non-empty {@link #fk()}). At serialization time the field is replaced by
 *   the flat pair {@code <name>_Uuid} + {@code <name>_Display}; the
 *   {@code DisplayRef} object itself is never emitted.</li>
 *   <li><b>Presentational scalar</b> — the raw, typed value stays under its
 *   own name and a {@code <name>_Display} string is added next to it.
 *   {@link #value()} picks the format; {@link Kind#AUTO} infers it from the
 *   runtime type (temporal → datetime, {@code Boolean} → yes/no,
 *   {@code Number} → locale separators, {@code String} → enum label).</li>
 * </ul>
 *
 * <p>Emission is handled by {@code DisplayBeanSerializerModifier}, wired into
 * the primary {@code ObjectMapper} in {@code JacksonConfig}. {@code _Display}
 * keys are output-only — Jackson ignores them on the way in.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
public @interface Display {

    /** Scalar format. Ignored when the field is a {@link DisplayRef} / {@link #fk()} is set. */
    Kind value() default Kind.AUTO;

    /**
     * Foreign-key relation key for {@link DisplayFormatter#fkLabel} (e.g.
     * {@code "allyType"}, {@code "promoterType"}). Non-empty marks the field
     * as a FK even if it is not typed {@link DisplayRef}. When empty and the
     * field is a {@link DisplayRef}, the field name is used as the relation
     * key.
     */
    String fk() default "";

    /**
     * Message-key scope for {@link Kind#ENUM} — {@code display.enum.<scope>.<VALUE>}.
     * Defaults to the field name.
     */
    String enumScope() default "";

    enum Kind { AUTO, DATETIME, DATE, MONEY, NUMBER, PERCENT, ENUM, BOOLEAN }
}
