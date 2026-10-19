package com.fenixcore.optibienestar360.modules.membership.entity;

import com.fenixcore.optibienestar360.core.jpa.CodedEnumConverter;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan.PlanType;
import jakarta.persistence.Converter;

/** Persists {@link PlanType} by its Spanish {@link PlanType#code()} ({@code plans.type}, {@code commission_tiers.plan_type}). */
@Converter(autoApply = true)
public class PlanTypeConverter extends CodedEnumConverter<PlanType> {

    public PlanTypeConverter() {
        super(PlanType.class);
    }
}
