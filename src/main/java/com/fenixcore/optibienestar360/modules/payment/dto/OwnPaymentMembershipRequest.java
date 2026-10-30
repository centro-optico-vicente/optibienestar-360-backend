package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Lets the payer point their own not-yet-approved payment at a membership
 * ({@code PUT /v1/me/payments/{uuid}/membership}, V201) — e.g. a relative paying the holder's plan.
 * The reviewer still verifies it on approval.
 */
public record OwnPaymentMembershipRequest(@NotNull UUID membershipUuid) {}
