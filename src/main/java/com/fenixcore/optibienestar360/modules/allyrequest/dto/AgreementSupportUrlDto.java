package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import java.time.Instant;

/** Short-lived link to the signed agreement support. */
public record AgreementSupportUrlDto(String url, Instant expiresAt) {}
