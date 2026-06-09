package com.fenixcore.optisaludplus.modules.membership.mapper;

import com.fenixcore.optisaludplus.modules.membership.dto.MembershipDto;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MembershipMapper {

    @Mapping(target = "memberUuid", source = "member.uuid")
    @Mapping(target = "planUuid",   source = "plan.uuid")
    @Mapping(target = "planCode",   source = "plan.code")
    @Mapping(target = "planName",   source = "plan.name")
    @Mapping(target = "planType",   source = "plan.type")
    MembershipDto toDto(Membership membership);
}
