package com.fenixcore.optibienestar360.modules.document.generic.dto;

import java.util.List;

public record GenericTableModel(
        String title,
        String subtitle,
        String generatedAt,
        String generatedBy,
        List<String> headers,
        List<List<String>> rows,
        int totalRecords
) {}
