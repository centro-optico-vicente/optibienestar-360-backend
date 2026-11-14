package com.fenixcore.optibienestar360.modules.member.dto;

/**
 * How {@code POST /v1/admin/members/advisor/distribute} splits the selection (ADR LOC-0016).
 * {@link #EQUAL}: round-robin over the targets in a stable order (name, then uuid).
 * {@link #COUNT}: each target gets exactly its {@code count}; members beyond the sum stay untouched.
 */
public enum MemberDistributionMode {
    EQUAL,
    COUNT
}
