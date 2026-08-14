package com.fenixcore.optibienestar360.modules.document.jasper;

public enum JasperFormat {
    PDF("application/pdf", ".pdf"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");

    private final String contentType;
    private final String fileExtension;

    JasperFormat(String contentType, String fileExtension) {
        this.contentType = contentType;
        this.fileExtension = fileExtension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
