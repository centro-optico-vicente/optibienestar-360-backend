package com.fenixcore.optibienestar360.modules.promoter.mapper;

import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PromoterMapper {

    @Mapping(target = "userUuid",         source = "user.uuid")
    @Mapping(target = "userEmail",        source = "user.email")
    @Mapping(target = "personUuid",       source = "person.uuid")
    @Mapping(target = "personFullName",   source = "person.fullName")
    @Mapping(target = "personRif",        source = "person.taxDocumentNumber")
    @Mapping(target = "promoterTypeUuid", source = "promoterType.uuid")
    @Mapping(target = "promoterTypeName", source = "promoterType.name")
    PromoterDto toDto(Promoter promoter);
}
