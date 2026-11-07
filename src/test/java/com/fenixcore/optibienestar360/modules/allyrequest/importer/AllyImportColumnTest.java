package com.fenixcore.optibienestar360.modules.allyrequest.importer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllyImportColumnTest {

    @Test
    void templateHeaders_mapWithOrWithoutMarkers() {
        for (AllyImportColumn c : AllyImportColumn.values()) {
            assertThat(AllyImportColumn.fromHeader(c.templateHeader())).contains(c);
            assertThat(AllyImportColumn.fromHeader(c.header())).contains(c);
            assertThat(AllyImportColumn.fromHeader(c.key())).contains(c);
        }
    }

    @Test
    void accentsCaseAndSymbols_areIgnored() {
        assertThat(AllyImportColumn.fromHeader("rif numero")).contains(AllyImportColumn.TAX_NUMBER);
        assertThat(AllyImportColumn.fromHeader("N° de sucursales")).contains(AllyImportColumn.BRANCHES);
        assertThat(AllyImportColumn.fromHeader("PUNTO DE VENTA")).contains(AllyImportColumn.POS);
        assertThat(AllyImportColumn.fromHeader("Columna rara")).isEmpty();
    }

    @Test
    void theCsvTemplate_isOneSemicolonHeaderRowWithBom() {
        String csv = new String(AllyImportTemplate.csv(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Segmento *;Nombre del aliado *;");
        assertThat(csv.trim().split(";")).hasSize(AllyImportColumn.imported().size());
    }
}
