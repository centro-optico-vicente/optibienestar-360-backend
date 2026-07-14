package com.fenixcore.optibienestar360.modules.payment.mapper;

import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "membershipUuid",       source = "membership.uuid")
    @Mapping(target = "memberUuid",           source = "membership.member.uuid")
    @Mapping(target = "planUuid",             source = "membership.plan.uuid")
    @Mapping(target = "planCode",             source = "membership.plan.code")
    @Mapping(target = "payerUserUuid",        source = "payerUser.uuid")
    @Mapping(target = "reviewedByUserUuid",   source = "reviewedBy.uuid")
    @Mapping(target = "supportFileAvailable", source = "supportFileUrl", qualifiedByName = "isPresent")
    PaymentDto toDto(Payment payment);

    @Named("isPresent")
    static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
