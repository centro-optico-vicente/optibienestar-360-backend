package com.fenixcore.optibienestar360.modules.promoter.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CommissionMapper {

    @Mapping(target = "promoter",     source = "promoter")
    @Mapping(target = "paymentUuid",  source = "payment.uuid")
    @Mapping(target = "memberUuid",   source = "member.uuid")
    @Mapping(target = "currency_Code", source = "currency.code")
    @Mapping(target = "amountConverted", ignore = true)
    @Mapping(target = "convertedCurrency_Code", ignore = true)
    @Mapping(target = "exchangeRateUsed", ignore = true)
    @Mapping(target = "exchangeRateDate", ignore = true)
    CommissionDto toDto(Commission commission);

    /** {@code promoter_Code} carries the referral code (Promoter has no generic {@code getCode()}). */
    default DisplayRef promoterRef(Promoter promoter) {
        return promoter == null ? null
                : DisplayRef.of(promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName());
    }
}
