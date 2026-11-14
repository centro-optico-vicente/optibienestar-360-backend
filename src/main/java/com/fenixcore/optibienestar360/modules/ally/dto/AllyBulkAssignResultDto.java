package com.fenixcore.optibienestar360.modules.ally.dto;

/**
 * Outcome of {@code POST /v1/admin/allies/advisor/bulk-assign}: {@code total} distinct allies in the
 * request, {@code skipped} of them already had that advisor (left untouched).
 */
public record AllyBulkAssignResultDto(int total, int skipped) {}
