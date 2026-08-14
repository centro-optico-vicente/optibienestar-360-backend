package com.fenixcore.optibienestar360.modules.document.service.renderers;

import com.fenixcore.optibienestar360.modules.document.jasper.JasperFormat;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperReportService;
import com.fenixcore.optibienestar360.modules.document.service.DocumentModel;
import com.fenixcore.optibienestar360.modules.document.service.DocumentRenderer;
import com.fenixcore.optibienestar360.modules.document.service.RenderedDocument;
import org.springframework.stereotype.Component;

@Component
public class JasperXlsxRenderer implements DocumentRenderer {

    private final JasperReportService jasperReportService;

    public JasperXlsxRenderer(JasperReportService jasperReportService) {
        this.jasperReportService = jasperReportService;
    }

    @Override
    public JasperFormat format() {
        return JasperFormat.XLSX;
    }

    @Override
    public RenderedDocument render(DocumentModel model) {
        if (!(model instanceof DocumentModel.JasperDocumentModel jasperModel)) {
            throw new IllegalArgumentException("Invalid model for JasperXlsxRenderer. Expected JasperDocumentModel.");
        }

        byte[] bytes = jasperReportService.generateReport(
                jasperModel.reportPath(),
                jasperModel.parameters(),
                jasperModel.data(),
                JasperFormat.XLSX
        );

        String fileName = (jasperModel.title() != null ? jasperModel.title() : "report") + JasperFormat.XLSX.getFileExtension();
        return new RenderedDocument(bytes, JasperFormat.XLSX.getContentType(), fileName);
    }
}
