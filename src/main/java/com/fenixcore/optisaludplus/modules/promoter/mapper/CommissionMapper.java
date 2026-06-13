package com.fenixcore.optisaludplus.modules.promoter.mapper;

import com.fenixcore.optisaludplus.modules.promoter.dto.CommissionDto;
import com.fenixcore.optisaludplus.modules.promoter.entity.Commission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CommissionMapper {

    @Mapping(target = "promoterUuid",        source = "promoter.uuid")
    @Mapping(target = "promoterCode",        source = "promoter.referralCode")
    @Mapping(target = "promoterDisplayName", source = "promoter.displayName")
    @Mapping(target = "paymentUuid",         source = "payment.uuid")
    @Mapping(target = "memberUuid",          source = "member.uuid")
    CommissionDto toDto(Commission commission);
}
