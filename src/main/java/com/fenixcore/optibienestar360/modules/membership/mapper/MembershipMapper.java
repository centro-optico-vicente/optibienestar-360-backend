package com.fenixcore.optibienestar360.modules.membership.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface MembershipMapper {

    @Mapping(target = "memberUuid", source = "member.uuid")
    @Mapping(target = "planUuid",   source = "plan.uuid")
    @Mapping(target = "planCode",   source = "plan.code")
    @Mapping(target = "planName",   source = "plan.name")
    @Mapping(target = "planType",   source = "plan.type")
    @Mapping(target = "campaign",   source = "campaign")
    @Mapping(target = "currency_Code", source = "currency.code")
    @Mapping(target = "amountConverted", ignore = true)
    @Mapping(target = "convertedCurrency_Code", ignore = true)
    @Mapping(target = "exchangeRateUsed", ignore = true)
    @Mapping(target = "exchangeRateDate", ignore = true)
    MembershipDto toDto(Membership membership);
}
