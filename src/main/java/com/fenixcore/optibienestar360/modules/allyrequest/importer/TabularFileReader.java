package com.fenixcore.optibienestar360.modules.allyrequest.importer;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the ally template into rows of text cells (hub ADR 0036): an Excel
 * workbook ({@code .xlsx}, sheet «Aliados» or else the first one), or a text
 * file separated by semicolon, comma or tab. Text files follow RFC 4180
 * quoting (a quoted cell may hold the separator, quotes doubled, line
 * breaks), may start with a UTF-8 BOM and may be UTF-8 or Windows-1252 (what
 * Excel writes for "CSV" on Windows). With {@code AUTO} the separator is the
 * one whose parse recognizes the most template headers.
 */
public final class TabularFileReader {

    /** Separator of a text file. */
    public enum Delimiter {
        AUTO(null), SEMICOLON(';'), COMMA(','), TAB('\t');

        private final Character symbol;

        Delimiter(Character symbol) { this.symbol = symbol; }

        public Character symbol() { return symbol; }

        public static Delimiter parse(String value) {
            if (value == null || value.isBlank()) return AUTO;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "auto" -> AUTO;
                case "semicolon", ";", "punto-y-coma" -> SEMICOLON;
                case "comma", ",", "coma" -> COMMA;
                case "tab", "\\t", "\t", "tabulacion", "tabulación" -> TAB;
                default -> throw new IllegalArgumentException("ally_import.delimiter.invalid");
            };
        }
    }

    public enum Format { XLSX, CSV }

    /** One physical row; {@code number} is 1-based as the spreadsheet / text editor shows it. */
    public record Line(int number, List<String> cells) {
        public String cell(int index) {
            return index >= 0 && index < cells.size() ? cells.get(index) : null;
        }
    }

    /** Parsed file: its format, the separator used (text only) and every row. */
    public record Table(Format format, Character delimiter, List<Line> lines) {}

    private static final String MAIN_SHEET = "Aliados";

    private TabularFileReader() {}

    public static Table read(String filename, byte[] bytes, Delimiter delimiter) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("ally_import.file.empty");
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean zip = bytes.length > 3 && bytes[0] == 'P' && bytes[1] == 'K';
        if (name.endsWith(".xlsx") || zip) {
            return new Table(Format.XLSX, null, readXlsx(bytes));
        }
        if (name.endsWith(".xls") || (bytes.length > 4 && (bytes[0] & 0xFF) == 0xD0 && (bytes[1] & 0xFF) == 0xCF)) {
            throw new IllegalArgumentException("ally_import.format.unsupported");
        }
        String text = decode(bytes);
        if (delimiter != Delimiter.AUTO) {
            return new Table(Format.CSV, delimiter.symbol(), parseText(text, delimiter.symbol()));
        }
        Character best = null;
        List<Line> bestLines = null;
        int bestScore = -1;
        for (char candidate : new char[]{';', ',', '\t'}) {
            List<Line> lines = parseText(text, candidate);
            int score = headerScore(lines);
            if (score > bestScore) {
                best = candidate;
                bestLines = lines;
                bestScore = score;
            }
        }
        return new Table(Format.CSV, best, bestLines);
    }

    /** Best number of recognized template headers in any of the first rows. */
    static int headerScore(List<Line> lines) {
        int best = 0;
        for (int i = 0; i < Math.min(lines.size(), 10); i++) {
            int score = 0;
            for (String cell : lines.get(i).cells()) {
                if (AllyImportColumn.fromHeader(cell).isPresent()) score++;
            }
            best = Math.max(best, score);
        }
        return best;
    }

    // ─── Excel ──────────────────────────────────────────────────────────────

    private static List<Line> readXlsx(byte[] bytes) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(MAIN_SHEET);
            if (sheet == null) sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<Line> lines = new ArrayList<>();
            for (Row row : sheet) {
                List<String> cells = new ArrayList<>();
                short last = row.getLastCellNum();
                for (int c = 0; c < Math.max(last, 0); c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                lines.add(new Line(row.getRowNum() + 1, cells));
            }
            return lines;
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException("ally_import.file.unreadable");
        }
    }

    // ─── Text ───────────────────────────────────────────────────────────────

    /** UTF-8 (BOM stripped) when valid, otherwise Windows-1252. */
    static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buffer).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, offset, bytes.length - offset, Charset.forName("windows-1252"));
        }
    }

    /** RFC 4180 parser for one separator; line numbers count physical lines. */
    static List<Line> parseText(String text, char delimiter) {
        List<Line> lines = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean cellStarted = false;
        int physicalLine = 1;
        int recordStart = 1;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                } else {
                    if (ch == '\n') physicalLine++;
                    cell.append(ch);
                }
                i++;
                continue;
            }
            if (ch == '"' && !cellStarted) {
                quoted = true;
                cellStarted = true;
            } else if (ch == delimiter) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
                cellStarted = false;
            } else if (ch == '\r' || ch == '\n') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
                cellStarted = false;
                lines.add(new Line(recordStart, cells));
                cells = new ArrayList<>();
                if (ch == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                physicalLine++;
                recordStart = physicalLine;
            } else {
                cell.append(ch);
                cellStarted = true;
            }
            i++;
        }
        if (cellStarted || cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString().trim());
            lines.add(new Line(recordStart, cells));
        }
        return lines;
    }
}
