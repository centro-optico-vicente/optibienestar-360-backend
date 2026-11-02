package com.fenixcore.optibienestar360.modules.membership.dto;

/**
 * Result of applying the plan's term rule: {@code applied} memberships were updated; {@code skipped} were
 * requested but left untouched (canceled, inactive, of another plan or not found).
 */
public record PlanTermApplyResultDto(int applied, int skipped) {}
