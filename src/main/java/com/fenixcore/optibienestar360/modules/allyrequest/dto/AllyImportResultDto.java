package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import java.util.List;

/**
 * Result of {@code POST /v1/admin/ally-requests/import} (hub ADR 0036).
 * {@code delimiter} is the separator used for a text file ({@code ";"},
 * {@code ","} or a tab), {@code null} for Excel. With errors nothing is
 * created, even when {@code dryRun} is false.
 */
public record AllyImportResultDto(
        boolean dryRun,
        String format,
        String delimiter,
        int totalRows,
        int validRows,
        int createdRows,
        List<RowError> errors
) {
    /** {@code row} is the row number the spreadsheet / text editor shows; {@code column} the template header. */
    public record RowError(int row, String column, String code, String message) {}
}
