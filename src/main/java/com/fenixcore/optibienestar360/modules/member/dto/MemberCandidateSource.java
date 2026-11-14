package com.fenixcore.optibienestar360.modules.member.dto;

/**
 * Where the members of the portfolio-distribution screen come from (ADR LOC-0016).
 * {@link #UNASSIGNED}: members nobody advises (no advisor or the INSTITUCION system advisor).
 * {@link #ADVISOR}: the current portfolio of one advisor, active or not, system included.
 */
public enum MemberCandidateSource {
    UNASSIGNED,
    ADVISOR
}
