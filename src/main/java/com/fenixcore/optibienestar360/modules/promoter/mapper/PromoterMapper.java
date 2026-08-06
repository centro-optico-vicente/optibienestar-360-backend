package com.fenixcore.optibienestar360.modules.promoter.mapper;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PromoterMapper {

    @Mapping(target = "userUuid",         source = "user.uuid")
    @Mapping(target = "personUuid",       source = "person.uuid")
    @Mapping(target = "promoterTypeUuid", source = "promoterType.uuid")
    @Mapping(target = "promoterTypeName", source = "promoterType.name")
    PromoterDto toDto(Promoter promoter);
}
