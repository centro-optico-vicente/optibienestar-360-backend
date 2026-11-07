package com.fenixcore.optibienestar360.modules.allyrequest.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The template as text: semicolon, comma or tab, detected from the header row. */
class TabularFileReaderTest {

    private static final String[] HEADER = {"Segmento *", "Nombre del aliado *", "Rubro *", "Horario"};
    private static final String[] ROW = {"Comercial", "Óptica Uno", "Centro óptico", "L-V 08:00-12:00, 14:00-18:00; S 08:00-12:00"};

    private static byte[] csv(char sep) {
        String header = String.join(String.valueOf(sep), HEADER);
        // The hours hold both ';' and ',': quoted like Excel does whatever the separator.
        String row = ROW[0] + sep + ROW[1] + sep + ROW[2] + sep + "\"" + ROW[3] + "\"";
        return ("﻿" + header + "\r\n" + row + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(chars = {';', ',', '\t'})
    void auto_detectsTheSeparator(char sep) {
        TabularFileReader.Table table = TabularFileReader.read("aliados.csv", csv(sep), TabularFileReader.Delimiter.AUTO);

        assertThat(table.format()).isEqualTo(TabularFileReader.Format.CSV);
        assertThat(table.delimiter()).isEqualTo(sep);
        assertThat(table.lines()).hasSize(2);
        assertThat(table.lines().get(0).cells()).containsExactly(HEADER);
        assertThat(table.lines().get(1).cells()).containsExactly(ROW);
        assertThat(table.lines().get(1).number()).isEqualTo(2);
    }

    @Test
    void anExplicitSeparator_isUsedAsIs() {
        TabularFileReader.Table table = TabularFileReader.read("aliados.txt", csv('\t'), TabularFileReader.Delimiter.TAB);
        assertThat(table.lines().get(1).cells()).containsExactly(ROW);
    }

    @Test
    void windows1252_isDecodedWhenTheFileIsNotUtf8() {
        byte[] bytes = "Segmento;Nombre del aliado\nComercial;Óptica Ñandú\n".getBytes(Charset.forName("windows-1252"));
        TabularFileReader.Table table = TabularFileReader.read("aliados.csv", bytes, TabularFileReader.Delimiter.AUTO);
        assertThat(table.lines().get(1).cells()).containsExactly("Comercial", "Óptica Ñandú");
    }

    @Test
    void quotedCells_keepDoubledQuotesAndLineBreaks() {
        byte[] bytes = "Segmento;Descripción\nComercial;\"Dice \"\"hola\"\"\nen dos líneas\"\nProfesional;x\n"
                .getBytes(StandardCharsets.UTF_8);
        TabularFileReader.Table table = TabularFileReader.read("a.csv", bytes, TabularFileReader.Delimiter.SEMICOLON);
        assertThat(table.lines().get(1).cells()).containsExactly("Comercial", "Dice \"hola\"\nen dos líneas");
        assertThat(table.lines().get(2).number()).isEqualTo(4);
    }

    @Test
    void legacyXls_isRejected() {
        byte[] ole = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0};
        assertThatThrownBy(() -> TabularFileReader.read("a.xls", ole, TabularFileReader.Delimiter.AUTO))
                .hasMessage("ally_import.format.unsupported");
    }

    @Test
    void delimiterParam_acceptsNamesAndSymbols() {
        assertThat(TabularFileReader.Delimiter.parse(null)).isEqualTo(TabularFileReader.Delimiter.AUTO);
        assertThat(TabularFileReader.Delimiter.parse("semicolon")).isEqualTo(TabularFileReader.Delimiter.SEMICOLON);
        assertThat(TabularFileReader.Delimiter.parse(",")).isEqualTo(TabularFileReader.Delimiter.COMMA);
        assertThat(TabularFileReader.Delimiter.parse("tab")).isEqualTo(TabularFileReader.Delimiter.TAB);
        assertThatThrownBy(() -> TabularFileReader.Delimiter.parse("pipe"))
                .hasMessage("ally_import.delimiter.invalid");
    }
}
