package com.fenixcore.optibienestar360.modules.organization.entity;

/**
 * Which allies the public allies counter on the login screen counts, by
 * segment (hub ADR 0042, V265 {@code organizations.allies_counter_mode}).
 */
public enum AlliesCounterMode {
    /** The allies card is not shown. */
    HIDDEN,
    /** Professional allies: segment {@code B2P}. */
    PROFESSIONAL,
    /** Commercial allies: segment {@code B2B}. */
    COMMERCIAL,
    /** Both segments (the behavior before V265). */
    COMMERCIAL_AND_PROFESSIONAL
}
