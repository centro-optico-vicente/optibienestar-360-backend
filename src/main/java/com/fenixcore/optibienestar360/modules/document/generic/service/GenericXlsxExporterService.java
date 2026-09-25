package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericTableModel;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class GenericXlsxExporterService {

    private final MessageSource messageSource;

    public GenericXlsxExporterService(@Autowired(required = false) MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public GenericXlsxExporterService() {
        this(null);
    }

    /**
     * Generates XLSX bytes from a GenericRecordModel using FastExcel.
     */
    public byte[] generateXlsx(GenericRecordModel model) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Workbook wb = new Workbook(out, "OptiBienestar 360", "1.0");
            Worksheet ws = wb.newWorksheet("Record Card");

            int row = 0;

            // Main header
            ws.value(row, 0, model.title());
            ws.style(row, 0).bold().fontSize(14).set();
            row++;

            if (model.subtitle() != null && !model.subtitle().isBlank()) {
                ws.value(row, 0, model.subtitle());
                ws.style(row, 0).italic().set();
                row++;
            }

            if (model.identifier() != null && !model.identifier().isBlank()) {
                ws.value(row, 0, "Identifier: " + model.identifier());
                row++;
            }

            ws.value(row, 0, "Generated: " + model.generatedAt() + " by " + model.generatedBy());
            row += 2;

            // Key-Value table of main fields
            ws.value(row, 0, "Field");
            ws.value(row, 1, "Value");
            ws.style(row, 0).bold().fillColor("E0E0E0").set();
            ws.style(row, 1).bold().fillColor("E0E0E0").set();
            row++;

            for (Map.Entry<String, String> entry : model.fields().entrySet()) {
                ws.value(row, 0, entry.getKey());
                ws.value(row, 1, entry.getValue());
                row++;
            }

            row++;

            // Detail sections
            for (GenericRecordModel.DetailSection section : model.detailSections()) {
                ws.value(row, 0, section.title());
                ws.style(row, 0).bold().fontSize(12).set();
                row++;

                int col = 0;
                for (String header : section.headers()) {
                    ws.value(row, col, header);
                    ws.style(row, col).bold().fillColor("F0F0F0").set();
                    col++;
                }
                row++;

                for (List<String> dataRow : section.rows()) {
                    col = 0;
                    for (String val : dataRow) {
                        ws.value(row, col, val);
                        col++;
                    }
                    row++;
                }
                row++;
            }

            wb.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error exporting generic XLSX report via FastExcel: " + e.getMessage(), e);
        }
    }

    /**
     * Generates XLSX bytes from a GenericTableModel (flat table) using FastExcel.
     */
    public byte[] generateTableXlsx(GenericTableModel model) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Workbook wb = new Workbook(out, "OptiBienestar 360", "1.0");
            Worksheet ws = wb.newWorksheet("List");

            int row = 0;

            // Main header
            ws.value(row, 0, model.title());
            ws.style(row, 0).bold().fontSize(14).set();
            row++;

            if (model.subtitle() != null && !model.subtitle().isBlank()) {
                ws.value(row, 0, model.subtitle());
                ws.style(row, 0).italic().set();
                row++;
            }

            ws.value(row, 0, "Generated: " + model.generatedAt() + " by " + model.generatedBy() + " (Total: " + model.totalRecords() + ")");
            row += 2;

            // Table headers
            int col = 0;
            for (String header : model.headers()) {
                ws.value(row, col, header);
                ws.style(row, col).bold().fillColor("0056B3").fontColor("FFFFFF").set();
                col++;
            }
            row++;

            // Data rows
            if (model.rows().isEmpty()) {
                Locale locale = LocaleContextHolder.getLocale();
                String emptyMessage = messageSource != null
                        ? messageSource.getMessage("document.table_list.no_data", null, "No records found to display", locale)
                        : "No records found to display";
                ws.value(row, 0, emptyMessage);
                ws.style(row, 0).italic().fontColor("64748B").set();
                row++;
            } else {
                for (List<String> dataRow : model.rows()) {
                    col = 0;
                    for (String val : dataRow) {
                        ws.value(row, col, val != null ? val : "");
                        col++;
                    }
                    row++;
                }
            }

            wb.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error exporting XLSX table via FastExcel: " + e.getMessage(), e);
        }
    }
}
