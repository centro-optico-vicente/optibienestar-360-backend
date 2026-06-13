package com.fenixcore.optisaludplus.modules.promoter.mapper;

import com.fenixcore.optisaludplus.modules.promoter.dto.PromoterDto;
import com.fenixcore.optisaludplus.modules.promoter.entity.Promoter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PromoterMapper {

    @Mapping(target = "userUuid",   source = "user.uuid")
    @Mapping(target = "personUuid", source = "person.uuid")
    PromoterDto toDto(Promoter promoter);
}
