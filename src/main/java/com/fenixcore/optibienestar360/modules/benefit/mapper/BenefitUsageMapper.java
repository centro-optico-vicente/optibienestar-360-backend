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
    @Mapping(target = "beneficiaryUuid",  source = "beneficiary.uuid")
    @Mapping(target = "consumedByName",   expression = "java(consumedByName(usage))")
    @Mapping(target = "beneficiaryRelationship", source = "beneficiary.relationship")
    @Mapping(target = "allyUuid",         source = "ally.uuid")
    @Mapping(target = "allyName",         source = "ally.name")
    @Mapping(target = "allyProductUuid",  source = "allyProduct.uuid")
    @Mapping(target = "allyProductName",  source = "allyProduct.product.name")
    @Mapping(target = "allyUserUuid",     source = "allyUser.uuid")
    @Mapping(target = "productCategoryUuid", source = "productCategory.uuid")
    @Mapping(target = "productCategoryName", source = "productCategory.name")
    @Mapping(target = "copayCurrency",    source = "copayCurrency.code")
    @Mapping(target = "consumptionCurrency", source = "consumptionCurrency.code")
    BenefitUsageDto toDto(BenefitUsage usage);

    /** The beneficiary who consumed, else the holder. */
    default String consumedByName(BenefitUsage usage) {
        if (usage.getBeneficiary() != null && usage.getBeneficiary().getPerson() != null) {
            return usage.getBeneficiary().getPerson().getFullName();
        }
        return usage.getMembership().getMember().getPerson().getFullName();
    }
}
