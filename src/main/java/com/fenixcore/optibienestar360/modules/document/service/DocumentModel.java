package com.fenixcore.optibienestar360.modules.document.service;

import java.util.Collection;
import java.util.Map;

public interface DocumentModel {

    record JasperDocumentModel(
            String reportPath,
            String title,
            Map<String, Object> parameters,
            Collection<?> data
    ) implements DocumentModel {}
}
