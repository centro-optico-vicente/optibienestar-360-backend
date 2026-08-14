package com.fenixcore.optibienestar360.modules.document.service;

import com.fenixcore.optibienestar360.modules.document.jasper.JasperFormat;

public interface DocumentRenderer {
    JasperFormat format();
    RenderedDocument render(DocumentModel model);
}
