package com.fenixcore.optibienestar360.modules.allyrequest.importer;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * The empty ally template (hub ADR 0036). The Excel file is the hub's
 * {@code docs/plantillas/plantilla-registro-aliados.xlsx}, copied to the
 * classpath — regenerate both together when the catalogs change. The CSV is
 * the header row of the same columns, semicolon separated (the list
 * separator of Excel in es-VE), UTF-8 with BOM so Excel opens the accents.
 */
public final class AllyImportTemplate {

    static final String XLSX_RESOURCE = "import-templates/plantilla-registro-aliados.xlsx";

    private AllyImportTemplate() {}

    public static byte[] xlsx() {
        try (InputStream in = new ClassPathResource(XLSX_RESOURCE).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("ally import template missing from the classpath", ex);
        }
    }

    public static byte[] csv() {
        String header = AllyImportColumn.imported().stream()
                .map(AllyImportColumn::templateHeader)
                .map(AllyImportTemplate::quote)
                .collect(Collectors.joining(";"));
        return ("﻿" + header + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String quote(String cell) {
        return cell.contains(";") || cell.contains("\"") ? "\"" + cell.replace("\"", "\"\"") + "\"" : cell;
    }
}
