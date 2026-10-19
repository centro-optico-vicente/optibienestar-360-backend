package com.fenixcore.optibienestar360.modules.terms.entity;

import com.fenixcore.optibienestar360.core.jpa.CodedEnumConverter;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import jakarta.persistence.Converter;

/** Persists {@link TermType} by its Spanish {@link TermType#code()} ({@code terms_versions.term_type}). */
@Converter(autoApply = true)
public class TermTypeConverter extends CodedEnumConverter<TermType> {

    public TermTypeConverter() {
        super(TermType.class);
    }
}
