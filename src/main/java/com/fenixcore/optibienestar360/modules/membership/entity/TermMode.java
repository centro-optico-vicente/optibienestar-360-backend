package com.fenixcore.optibienestar360.modules.membership.entity;

/**
 * How a plan (and the membership snapshot) defines the end of a term, hub ADR 0034.
 * <ul>
 *   <li>{@code FIXED_DATE} — a fixed annual date (month/day, e.g. December 1st).</li>
 *   <li>{@code PERIOD} — N months after the term start (enrollment for the first term).</li>
 * </ul>
 */
public enum TermMode {
    FIXED_DATE, PERIOD
}
