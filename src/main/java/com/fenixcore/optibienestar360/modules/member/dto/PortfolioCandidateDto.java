package com.fenixcore.optibienestar360.modules.member.dto;

import java.util.UUID;

/**
 * A member an advisor can collect from ({@code GET /v1/advisors/me/portfolio/candidates}, V201):
 * {@code inPortfolio} is false for a member nobody advises, who joins the portfolio on the first collection.
 */
public record PortfolioCandidateDto(UUID uuid, String documentNumber, String fullName, boolean inPortfolio) {}
