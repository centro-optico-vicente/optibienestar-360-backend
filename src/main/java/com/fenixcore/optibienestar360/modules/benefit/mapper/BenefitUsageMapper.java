package com.fenixcore.optibienestar360.modules.benefit.mapper;

import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BenefitUsageMapper {

    @Mapping(target = "membershipUuid",       source = "membership.uuid")
    @Mapping(target = "memberUuid",           source = "membership.member.uuid")
    @Mapping(target = "memberFullName",       source = "membership.member.person.fullName")
    @Mapping(target = "memberDocumentType",   source = "membership.member.person.documentType")
    @Mapping(target = "memberDocumentNumber", source = "membership.member.person.documentNumber")
    @Mapping(target = "planUuid",         source = "membership.plan.uuid")
    @Mapping(target = "planCode",         source = "membership.plan.code")
    @Mapping(target = "allyUuid",         source = "ally.uuid")
    @Mapping(target = "allyName",         source = "ally.name")
    @Mapping(target = "allyServiceUuid",  source = "allyService.uuid")
    @Mapping(target = "allyUserUuid",     source = "allyUser.uuid")
    BenefitUsageDto toDto(BenefitUsage usage);
}
