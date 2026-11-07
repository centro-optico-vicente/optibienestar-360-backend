package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import jakarta.validation.constraints.Size;

/** Rejection of an ally request; {@code reason} is required (422) and emailed to the applicant. */
public record AllyRequestRejectRequest(@Size(max = 1000) String reason) {}
