package com.fenixcore.optibienestar360.modules.promotion.dto;

/** 1:1 sub-resource of a membership — always 200 with {@code exists} (hub empty-state convention). */
public record MembershipPromotionStatusDto(boolean exists, MembershipPromotionDto promotion) {}
