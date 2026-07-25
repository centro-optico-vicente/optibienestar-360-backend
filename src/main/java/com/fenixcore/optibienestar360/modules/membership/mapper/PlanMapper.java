package com.fenixcore.optibienestar360.modules.membership.mapper;

import com.fenixcore.optibienestar360.modules.membership.dto.PlanDto;
import com.fenixcore.optibienestar360.modules.membership.dto.PublicPlanDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    PlanDto toDto(Plan plan);

    /** Sanitized projection for the anonymous pricing surface — see {@link PublicPlanDto}. */
    PublicPlanDto toPublicDto(Plan plan);
}
