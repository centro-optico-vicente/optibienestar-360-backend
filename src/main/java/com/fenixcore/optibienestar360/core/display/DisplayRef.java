package com.fenixcore.optibienestar360.core.display;

import java.util.UUID;

/**
 * Compact carrier for a foreign-key relation inside a DTO: the target's
 * {@code uuid} plus the two fields {@link DisplayFormatter#fkLabel} needs to
 * build a label ({@code code} / {@code name}, or a person's document number /
 * full name for person-shaped relations).
 *
 * <p>Never serialized as an object — {@code DisplayBeanSerializerModifier}
 * flattens a {@code @Display}-annotated {@code DisplayRef} field into the
 * pair {@code <name>_Uuid} + {@code <name>_Display} (ADR 0014). Mappers build
 * it from the loaded association, typically via {@link DisplayRefs}.</p>
 */
public record DisplayRef(UUID uuid, String code, String name) {

    /** {@code null} when every part is {@code null} (an absent optional FK). */
    public static DisplayRef of(UUID uuid, String code, String name) {
        return (uuid == null && code == null && name == null) ? null : new DisplayRef(uuid, code, name);
    }
}
