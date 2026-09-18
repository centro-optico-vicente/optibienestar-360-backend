package com.fenixcore.optibienestar360.modules.membership.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.membership.dto.PlanDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PublicPlanDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface PlanMapper {

    @Mapping(target = "currency_Code", source = "currency.code")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "amountConverted", ignore = true)
    @Mapping(target = "convertedCurrency_Code", ignore = true)
    @Mapping(target = "exchangeRateUsed", ignore = true)
    @Mapping(target = "exchangeRateDate", ignore = true)
    PlanDto toDto(Plan plan);

    /** Sanitized projection for the anonymous pricing surface — see {@link PublicPlanDto}. */
    PublicPlanDto toPublicDto(Plan plan);
}
