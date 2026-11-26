package com.fenixcore.optibienestar360.modules.organization.entity;

/**
 * What the public members counter on the login screen counts (hub ADR 0042,
 * V265 {@code organizations.members_counter_mode}).
 */
public enum MembersCounterMode {
    /** The members card is not shown. */
    HIDDEN,
    /** Holders whose membership is {@code ACTIVE}. */
    MEMBERS,
    /** {@link #MEMBERS} plus the active beneficiaries of those holders. */
    MEMBERS_AND_BENEFICIARIES
}
