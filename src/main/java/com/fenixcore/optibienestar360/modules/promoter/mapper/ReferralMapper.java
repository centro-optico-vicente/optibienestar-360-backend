package com.fenixcore.optibienestar360.modules.promoter.mapper;

import com.fenixcore.optibienestar360.modules.promoter.dto.MyReferralDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Referral;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps {@link Referral} → {@link MyReferralDto} for the affiliate's own
 * referrals surface. Source-path mappings (referred.uuid, etc.) emit
 * null-safe accessors when MapStruct generates the impl, so PENDING /
 * EXPIRED rows (referred null) map cleanly without an explicit guard.
 */
@Mapper(componentModel = "spring")
public interface ReferralMapper {

    @Mapping(target = "referredMemberUuid", source = "referred.uuid")
    @Mapping(target = "referredMemberName", source = "referred.person.fullName")
    @Mapping(target = "rewardPaymentUuid",  source = "rewardPayment.uuid")
    @Mapping(target = "rewardCurrency",     source = "rewardCurrency.code")
    MyReferralDto toMyDto(Referral referral);
}
