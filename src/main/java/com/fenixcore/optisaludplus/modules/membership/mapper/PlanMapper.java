package com.fenixcore.optisaludplus.modules.membership.mapper;

import com.fenixcore.optisaludplus.modules.membership.dto.PlanDto;
import com.fenixcore.optisaludplus.modules.membership.entity.Plan;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    PlanDto toDto(Plan plan);
}
