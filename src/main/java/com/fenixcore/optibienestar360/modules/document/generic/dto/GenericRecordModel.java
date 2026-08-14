package com.fenixcore.optibienestar360.modules.document.generic.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record GenericRecordModel(
        String title,
        String subtitle,
        String identifier,
        String generatedAt,
        String generatedBy,
        Map<String, String> fields,
        List<DetailSection> detailSections
) {
    public GenericRecordModel {
        if (fields == null) fields = Collections.emptyMap();
        if (detailSections == null) detailSections = Collections.emptyList();
    }

    public record DetailSection(
            String title,
            List<String> headers,
            List<List<String>> rows
    ) {}
}
