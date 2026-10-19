package com.fenixcore.optibienestar360.core.jpa;

import com.fenixcore.optibienestar360.core.entity.CodedEnum;
import jakarta.persistence.AttributeConverter;

/**
 * Persists a {@link CodedEnum} by its {@link CodedEnum#code()} instead of the
 * Java constant name. Subclass it once per enum and annotate the subclass with
 * {@code @Converter(autoApply = true)}; the entity field must not carry
 * {@code @Enumerated}.
 */
public abstract class CodedEnumConverter<E extends Enum<E> & CodedEnum> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected CodedEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CodedEnum.fromCode(type, dbData);
    }
}
