package com.fenixcore.optisaludplus.modules.benefit.mapper;

import com.fenixcore.optisaludplus.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optisaludplus.modules.benefit.entity.BenefitUsage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BenefitUsageMapper {

    @Mapping(target = "membershipUuid",   source = "membership.uuid")
    @Mapping(target = "memberUuid",       source = "membership.member.uuid")
    @Mapping(target = "planUuid",         source = "membership.plan.uuid")
    @Mapping(target = "planCode",         source = "membership.plan.code")
    @Mapping(target = "allyUuid",         source = "ally.uuid")
    @Mapping(target = "allyName",         source = "ally.name")
    @Mapping(target = "allyServiceUuid",  source = "allyService.uuid")
    @Mapping(target = "allyUserUuid",     source = "allyUser.uuid")
    BenefitUsageDto toDto(BenefitUsage usage);
}
