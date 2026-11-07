package com.fenixcore.optibienestar360.modules.allyrequest.importer;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Columns of the ally template ({@code docs/plantillas/plantilla-registro-aliados.xlsx}
 * in the hub, hub ADR 0036). The importer maps a header by its normalized
 * text — accents, case, punctuation and the {@code *} / {@code ◆C} /
 * {@code ◆P} markers are ignored — or by the English key, so a file exported
 * from the template as CSV maps the same way.
 */
public enum AllyImportColumn {
    SEGMENT("segment", "Segmento", Level.ALL),
    NAME("name", "Nombre del aliado", Level.ALL),
    TRADE_NAME("tradeName", "Nombre comercial", Level.B2B_ONLY),
    TAX_TYPE("taxDocumentType", "RIF tipo", Level.B2B),
    TAX_NUMBER("taxDocumentNumber", "RIF número", Level.B2B),
    ALLY_TYPE("allyType", "Rubro", Level.ALL),
    PROFESSION("profession", "Profesión / especialidad", Level.B2P),
    SUB_PROFESSION("subProfession", "Sub-especialidad", Level.B2P_ONLY),
    FIRST_NAMES("firstName", "Titular / contacto: nombres", Level.ALL),
    LAST_NAMES("lastName", "Titular / contacto: apellidos", Level.ALL),
    ID_TYPE("documentType", "Cédula tipo", Level.B2P),
    ID_NUMBER("documentNumber", "Cédula número", Level.B2P),
    POSITION("representativePosition", "Cargo del contacto", Level.B2B_ONLY),
    MOBILE("mobile", "Celular / WhatsApp", Level.ALL),
    PHONE("phone", "Teléfono fijo", Level.OPTIONAL),
    EMAIL("email", "Correo electrónico", Level.ALL),
    STATE("state", "Estado", Level.ALL),
    CITY("city", "Ciudad", Level.ALL),
    ADDRESS("address", "Dirección", Level.ALL),
    MAPS("googleMapsUrl", "Enlace Google Maps", Level.OPTIONAL),
    INSTAGRAM("instagram", "Instagram", Level.OPTIONAL),
    FACEBOOK("facebook", "Facebook", Level.OPTIONAL),
    WEBSITE("website", "Sitio web", Level.OPTIONAL),
    ZONE("commercialZone", "Zona comercial", Level.B2B_ONLY),
    FOUNDED("foundedYear", "Año de fundación", Level.B2B_ONLY),
    BRANCHES("branchesCount", "Nº de sucursales", Level.B2B_ONLY),
    REGISTERS("cashRegistersCount", "Nº de cajas", Level.B2B_ONLY),
    POS("hasPos", "¿Punto de venta?", Level.B2B_ONLY),
    HOST("hostName", "Clínica / centro donde atiende", Level.B2P_ONLY),
    OFFICE("officeNumber", "Consultorio Nº", Level.B2P_ONLY),
    APPOINTMENT_PHONE("appointmentPhone", "Teléfono de citas", Level.B2P_ONLY),
    ASSISTANT("assistantName", "Asistente", Level.B2P_ONLY),
    LICENSE1_BODY("license1Body", "Colegiatura 1: ente", Level.B2P_ONLY),
    LICENSE1_NUMBER("license1Number", "Colegiatura 1: número", Level.B2P_ONLY),
    LICENSE2_BODY("license2Body", "Colegiatura 2: ente", Level.B2P_ONLY),
    LICENSE2_NUMBER("license2Number", "Colegiatura 2: número", Level.B2P_ONLY),
    HOURS("openingHours", "Horario", Level.OPTIONAL),
    BENEFIT("benefit", "Beneficio / descuento ofrecido", Level.OPTIONAL),
    ADVISOR("advisorCode", "Código del asesor captador", Level.OPTIONAL),
    DESCRIPTION("description", "Descripción", Level.OPTIONAL),
    NOTES("notes", "Observaciones (no se importa)", Level.IGNORED);

    /**
     * Required for every row ({@code ALL}), required for one segment
     * ({@code B2B} / {@code B2P}), optional but only meaningful for one segment
     * ({@code *_ONLY}), optional for both, or never imported.
     */
    public enum Level { ALL, B2B, B2P, B2B_ONLY, B2P_ONLY, OPTIONAL, IGNORED }

    private static final Map<String, AllyImportColumn> BY_HEADER = new HashMap<>();

    static {
        for (AllyImportColumn c : values()) {
            BY_HEADER.put(normalize(c.header), c);
            BY_HEADER.put(normalize(c.key), c);
        }
        // Short forms people type by hand.
        BY_HEADER.put(normalize("Nombre"), NAME);
        BY_HEADER.put(normalize("RIF"), TAX_NUMBER);
        BY_HEADER.put(normalize("Profesión"), PROFESSION);
        BY_HEADER.put(normalize("Especialidad"), PROFESSION);
        BY_HEADER.put(normalize("Nombres"), FIRST_NAMES);
        BY_HEADER.put(normalize("Apellidos"), LAST_NAMES);
        BY_HEADER.put(normalize("Cédula"), ID_NUMBER);
        BY_HEADER.put(normalize("Celular"), MOBILE);
        BY_HEADER.put(normalize("WhatsApp"), MOBILE);
        BY_HEADER.put(normalize("Correo"), EMAIL);
        BY_HEADER.put(normalize("Observaciones"), NOTES);
    }

    private final String key;
    private final String header;
    private final Level level;

    AllyImportColumn(String key, String header, Level level) {
        this.key = key;
        this.header = header;
        this.level = level;
    }

    public String key() { return key; }
    public String header() { return header; }
    public Level level() { return level; }

    /** The template header with its marker: {@code *}, {@code ◆C} or {@code ◆P}. */
    public String templateHeader() {
        return switch (level) {
            case ALL -> header + " *";
            case B2B -> header + " ◆C";
            case B2P -> header + " ◆P";
            default -> header;
        };
    }

    public static Optional<AllyImportColumn> fromHeader(String text) {
        return Optional.ofNullable(BY_HEADER.get(normalize(text)));
    }

    public static List<AllyImportColumn> imported() {
        return Arrays.stream(values()).filter(c -> c.level != Level.IGNORED).toList();
    }

    /** Lowercase, no accents, no markers or punctuation, single spaces. */
    static String normalize(String text) {
        if (text == null) return "";
        String s = text.replace("◆C", "").replace("◆P", "").replace("◆c", "").replace("◆p", "").replace("◆", "");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.toLowerCase(Locale.ROOT).replace("nº", "n").replace("n°", "n").replace("º", "").replace("°", "");
        s = s.replaceAll("[^a-z0-9]+", " ").trim();
        return s;
    }
}
