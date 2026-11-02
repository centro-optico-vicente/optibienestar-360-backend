package com.fenixcore.optibienestar360.modules.dashboard.dto;

/**
 * Blocks of the home dashboard ({@code GET /v1/me/dashboard}). Each one is
 * included only when the caller holds the matching authorities and, for the
 * self-service ones, actually owns the underlying record (promoter, ally
 * operator, member or beneficiary with access).
 */
public enum DashboardSection {
    /** Staff view (ADMINISTRADOR / OPERADOR / SYSTEM): program-wide figures. */
    MANAGEMENT,
    /** Advisor's own portfolio ({@code PROMOTER_VIEW_OWN}). */
    ADVISOR,
    /** Ally operator's own establishments ({@code ALLY_VIEW_OWN}). */
    ALLY,
    /** Affiliate or beneficiary with access ({@code MEMBER_VIEW_OWN}). */
    MEMBER
}
