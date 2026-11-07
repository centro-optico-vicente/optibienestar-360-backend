package com.fenixcore.optibienestar360.modules.allyrequest.importer;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyHolderPersonRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyOpeningHour;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyOpeningHourRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyImportResultDto;
import com.fenixcore.optibienestar360.modules.allyrequest.dto.AllyImportResultDto.RowError;
import com.fenixcore.optibienestar360.modules.allyrequest.importer.AllyImportColumn.Level;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.LicensingBody;
import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import com.fenixcore.optibienestar360.modules.catalog.entity.State;
import com.fenixcore.optibienestar360.modules.catalog.repository.AllyTypeRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.CityRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.LicensingBodyRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.ProfessionRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.StateRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.entity.PersonProfessionalLicense;
import com.fenixcore.optibienestar360.modules.person.repository.PersonProfessionalLicenseRepository;
import com.fenixcore.optibienestar360.modules.person.service.PersonService;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Bulk import of allies from the template (hub ADR 0036): an Excel workbook
 * or a text file separated by semicolon, comma or tab
 * ({@link TabularFileReader}). Every row is validated against the same rules
 * the template shows — required columns per segment, catalog values by name
 * or code, the rubro of the row's segment, opening-hours format, duplicates
 * in the file and in the database — and reported by row and column.
 *
 * <p>{@code dryRun} only validates. Otherwise, if every row is valid, all of
 * them are created in one transaction: allies {@code PENDING} /
 * {@code IMPORT}, unpublished and without a login (staff invite the contact
 * later), with their holder person, professions, licenses and opening hours.
 * If any row has an error nothing is created.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AllyImportService {

    static final int MAX_ROWS = 2000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DIGITS = Pattern.compile("^\\d{1,20}$");
    private static final Pattern ID_NUMBER = Pattern.compile("^\\d{5,9}$");

    private final AlliesService alliesService;
    private final AllyRepository allyRepository;
    private final AllyOpeningHourRepository openingHourRepository;
    private final AllyTypeRepository allyTypeRepository;
    private final ProfessionRepository professionRepository;
    private final StateRepository stateRepository;
    private final CityRepository cityRepository;
    private final LicensingBodyRepository licensingBodyRepository;
    private final PromoterRepository promoterRepository;
    private final PersonService personService;
    private final PersonProfessionalLicenseRepository licenseRepository;
    private final MessageSource messageSource;

    @Transactional
    public AllyImportResultDto importFile(MultipartFile file, boolean dryRun, String delimiterParam) {
        TabularFileReader.Delimiter delimiter = TabularFileReader.Delimiter.parse(delimiterParam);
        byte[] bytes;
        try {
            bytes = file == null ? null : file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("ally_import.file.unreadable");
        }
        TabularFileReader.Table table = TabularFileReader.read(file == null ? null : file.getOriginalFilename(),
                bytes, delimiter);

        int headerIndex = findHeaderRow(table.lines());
        if (headerIndex < 0) {
            throw new IllegalArgumentException("ally_import.header.not_found");
        }
        Map<AllyImportColumn, Integer> columns = mapColumns(table.lines().get(headerIndex));
        if (!columns.containsKey(AllyImportColumn.SEGMENT)) {
            throw new IllegalArgumentException("ally_import.header.not_found");
        }

        List<TabularFileReader.Line> dataLines = table.lines().subList(headerIndex + 1, table.lines().size()).stream()
                .filter(line -> !isBlankRow(line, columns))
                .toList();
        if (dataLines.size() > MAX_ROWS) {
            throw new IllegalArgumentException("ally_import.too_many_rows");
        }

        Catalogs catalogs = loadCatalogs();
        List<RowError> errors = new ArrayList<>();
        List<ParsedRow> parsed = new ArrayList<>();
        Map<String, Integer> seenTax = new HashMap<>();
        Map<String, Integer> seenEmail = new HashMap<>();
        Map<String, Integer> seenProfessional = new HashMap<>();
        Locale locale = LocaleContextHolder.getLocale();
        for (TabularFileReader.Line line : dataLines) {
            RowContext ctx = new RowContext(line, columns, errors, locale);
            ParsedRow row = parseRow(ctx, catalogs);
            if (row == null) continue;
            if (row.taxNumber() != null) {
                String key = row.taxType() + row.taxNumber();
                Integer first = seenTax.putIfAbsent(key, line.number());
                if (first != null) ctx.error(AllyImportColumn.TAX_NUMBER, "duplicate_in_file", row.taxType() + "-" + row.taxNumber(), first);
                else if (allyRepository.existsByTaxDocumentTypeAndTaxDocumentNumber(row.taxType(), row.taxNumber())) {
                    ctx.error(AllyImportColumn.TAX_NUMBER, "tax_document_exists", row.taxType() + "-" + row.taxNumber());
                }
            }
            Integer firstEmail = seenEmail.putIfAbsent(row.email(), line.number());
            if (firstEmail != null) ctx.error(AllyImportColumn.EMAIL, "duplicate_in_file", row.email(), firstEmail);
            if (row.segment() == Segment.B2P && row.idNumber() != null) {
                String key = row.idType() + row.idNumber() + "|" + row.city().getId() + "|"
                        + row.professions().getFirst().getId();
                Integer first = seenProfessional.putIfAbsent(key, line.number());
                if (first != null) ctx.error(AllyImportColumn.ID_NUMBER, "duplicate_in_file", row.idType() + "-" + row.idNumber(), first);
                else if (professionalExists(row)) ctx.error(AllyImportColumn.ID_NUMBER, "duplicate_professional", row.idType() + "-" + row.idNumber());
            }
            if (!ctx.failed()) parsed.add(row);
        }

        String delimiterText = table.delimiter() == null ? null : String.valueOf(table.delimiter());
        int valid = parsed.size();
        if (dryRun || !errors.isEmpty()) {
            return new AllyImportResultDto(dryRun, table.format().name(), delimiterText, dataLines.size(), valid, 0, errors);
        }
        // A database rule not covered by the validation throws and rolls the whole import back.
        for (ParsedRow row : parsed) {
            create(row);
        }
        log.info("Ally import: {} allies created ({} file{})", parsed.size(), table.format(),
                delimiterText == null ? "" : ", delimiter '" + delimiterText + "'");
        return new AllyImportResultDto(false, table.format().name(), delimiterText, dataLines.size(), valid,
                parsed.size(), List.of());
    }

    // ─── Header ─────────────────────────────────────────────────────────────

    private static int findHeaderRow(List<TabularFileReader.Line> lines) {
        for (int i = 0; i < Math.min(lines.size(), 10); i++) {
            for (String cell : lines.get(i).cells()) {
                if (AllyImportColumn.fromHeader(cell).filter(c -> c == AllyImportColumn.SEGMENT).isPresent()) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Map<AllyImportColumn, Integer> mapColumns(TabularFileReader.Line header) {
        Map<AllyImportColumn, Integer> map = new EnumMap<>(AllyImportColumn.class);
        for (int i = 0; i < header.cells().size(); i++) {
            int index = i;
            AllyImportColumn.fromHeader(header.cells().get(i)).ifPresent(c -> map.putIfAbsent(c, index));
        }
        return map;
    }

    private static boolean isBlankRow(TabularFileReader.Line line, Map<AllyImportColumn, Integer> columns) {
        for (Map.Entry<AllyImportColumn, Integer> e : columns.entrySet()) {
            if (e.getKey().level() == Level.IGNORED) continue;
            String v = line.cell(e.getValue());
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    // ─── Row validation ─────────────────────────────────────────────────────

    private ParsedRow parseRow(RowContext ctx, Catalogs cat) {
        String segmentText = ctx.value(AllyImportColumn.SEGMENT);
        Segment segment = parseSegment(segmentText);
        if (segmentText == null) {
            ctx.error(AllyImportColumn.SEGMENT, "required");
            return null;
        }
        if (segment == null) {
            ctx.error(AllyImportColumn.SEGMENT, "invalid_value", segmentText);
            return null;
        }
        String segmentLabel = segment == Segment.B2B ? "Comercial" : "Profesional";

        for (AllyImportColumn c : AllyImportColumn.values()) {
            boolean required = c.level() == Level.ALL
                    || (c.level() == Level.B2B && segment == Segment.B2B)
                    || (c.level() == Level.B2P && segment == Segment.B2P);
            if (required && ctx.value(c) == null) {
                if (c.level() == Level.ALL) ctx.error(c, "required");
                else ctx.error(c, "required_for_segment", segmentLabel);
            }
            boolean foreign = ((c.level() == Level.B2B_ONLY || c.level() == Level.B2B) && segment == Segment.B2P
                    && c != AllyImportColumn.TAX_TYPE && c != AllyImportColumn.TAX_NUMBER)
                    || (c.level() == Level.B2P_ONLY && segment == Segment.B2B);
            if (foreign && ctx.value(c) != null) {
                ctx.error(c, "not_for_segment", segmentLabel);
            }
        }

        AllyType type = lookup(ctx, AllyImportColumn.ALLY_TYPE, cat.allyTypes());
        if (type != null && type.getSegment() != null && type.getSegment() != segment) {
            ctx.error(AllyImportColumn.ALLY_TYPE, "type_segment_mismatch", type.getName(), segmentLabel);
        }

        String taxType = upper(ctx.value(AllyImportColumn.TAX_TYPE));
        String taxNumber = digitsOnly(ctx.value(AllyImportColumn.TAX_NUMBER));
        if (taxType != null && !taxType.matches("^[JVEGP]$")) ctx.error(AllyImportColumn.TAX_TYPE, "invalid_value", taxType);
        if (taxNumber != null && !DIGITS.matcher(taxNumber).matches()) ctx.error(AllyImportColumn.TAX_NUMBER, "invalid_value", taxNumber);
        if ((taxType == null) != (taxNumber == null)) {
            ctx.error(taxType == null ? AllyImportColumn.TAX_TYPE : AllyImportColumn.TAX_NUMBER, "required");
        }

        List<Profession> professions = new ArrayList<>();
        Profession profession = segment == Segment.B2P ? lookup(ctx, AllyImportColumn.PROFESSION, cat.professions()) : null;
        Profession sub = segment == Segment.B2P ? lookup(ctx, AllyImportColumn.SUB_PROFESSION, cat.professions()) : null;
        if (sub != null && profession != null && (sub.getParent() == null
                || !Objects.equals(sub.getParent().getId(), profession.getId()))) {
            ctx.error(AllyImportColumn.SUB_PROFESSION, "sub_profession_mismatch", sub.getName(), profession.getName());
        }
        if (sub != null) professions.add(sub);
        else if (profession != null) professions.add(profession);

        String idType = upper(ctx.value(AllyImportColumn.ID_TYPE));
        String idNumber = digitsOnly(ctx.value(AllyImportColumn.ID_NUMBER));
        if (idType != null && !idType.matches("^[VE]$")) ctx.error(AllyImportColumn.ID_TYPE, "invalid_value", idType);
        if (idNumber != null && !ID_NUMBER.matcher(idNumber).matches()) ctx.error(AllyImportColumn.ID_NUMBER, "invalid_value", idNumber);
        if ((idType == null) != (idNumber == null)) {
            ctx.error(idType == null ? AllyImportColumn.ID_TYPE : AllyImportColumn.ID_NUMBER, "required");
        }

        String email = lower(ctx.value(AllyImportColumn.EMAIL));
        if (email != null && !EMAIL.matcher(email).matches()) ctx.error(AllyImportColumn.EMAIL, "invalid_value", email);

        State state = lookup(ctx, AllyImportColumn.STATE, cat.states());
        City city = null;
        String cityText = ctx.value(AllyImportColumn.CITY);
        if (state != null && cityText != null) {
            city = cat.cities().getOrDefault(state.getId(), Map.of()).get(AllyImportColumn.normalize(cityText));
            if (city == null) ctx.error(AllyImportColumn.CITY, "not_found", cityText);
        }

        Short founded = parseShort(ctx, AllyImportColumn.FOUNDED, 1800, 2100);
        Integer branches = parseInt(ctx, AllyImportColumn.BRANCHES);
        Integer registers = parseInt(ctx, AllyImportColumn.REGISTERS);
        Boolean pos = parseYesNo(ctx, AllyImportColumn.POS);

        List<LicenseData> licenses = new ArrayList<>();
        licenseOf(ctx, cat, AllyImportColumn.LICENSE1_BODY, AllyImportColumn.LICENSE1_NUMBER, licenses);
        licenseOf(ctx, cat, AllyImportColumn.LICENSE2_BODY, AllyImportColumn.LICENSE2_NUMBER, licenses);
        if (!licenses.isEmpty() && idNumber == null) ctx.error(AllyImportColumn.ID_NUMBER, "required_for_licenses");

        List<OpeningHoursParser.Range> hours = List.of();
        String hoursText = ctx.value(AllyImportColumn.HOURS);
        if (hoursText != null) {
            try {
                hours = OpeningHoursParser.parse(hoursText);
            } catch (IllegalArgumentException ex) {
                ctx.error(AllyImportColumn.HOURS, "hours_invalid", ex.getMessage());
            }
        }

        Promoter promoter = null;
        String advisor = upper(ctx.value(AllyImportColumn.ADVISOR));
        if (advisor != null) {
            promoter = promoterRepository.findByReferralCode(advisor).orElse(null);
            if (promoter == null) ctx.error(AllyImportColumn.ADVISOR, "not_found", advisor);
        }

        if (ctx.failed()) return null;
        return new ParsedRow(segment, ctx.value(AllyImportColumn.NAME), ctx.value(AllyImportColumn.TRADE_NAME),
                taxType, taxNumber, type, professions,
                ctx.value(AllyImportColumn.FIRST_NAMES), ctx.value(AllyImportColumn.LAST_NAMES), idType, idNumber,
                ctx.value(AllyImportColumn.POSITION), ctx.value(AllyImportColumn.MOBILE), ctx.value(AllyImportColumn.PHONE),
                email, city, ctx.value(AllyImportColumn.ADDRESS), ctx.value(AllyImportColumn.MAPS),
                stripAt(ctx.value(AllyImportColumn.INSTAGRAM)), ctx.value(AllyImportColumn.FACEBOOK),
                ctx.value(AllyImportColumn.WEBSITE), ctx.value(AllyImportColumn.ZONE), founded, branches, registers, pos,
                ctx.value(AllyImportColumn.HOST), ctx.value(AllyImportColumn.OFFICE),
                ctx.value(AllyImportColumn.APPOINTMENT_PHONE), ctx.value(AllyImportColumn.ASSISTANT),
                licenses, hours, ctx.value(AllyImportColumn.BENEFIT), promoter, ctx.value(AllyImportColumn.DESCRIPTION));
    }

    private void licenseOf(RowContext ctx, Catalogs cat, AllyImportColumn bodyCol, AllyImportColumn numberCol,
                           List<LicenseData> out) {
        String bodyText = ctx.value(bodyCol);
        String number = ctx.value(numberCol);
        if (bodyText == null && number == null) return;
        if (bodyText == null) { ctx.error(bodyCol, "required"); return; }
        if (number == null) { ctx.error(numberCol, "required"); return; }
        LicensingBody body = lookup(ctx, bodyCol, cat.licensingBodies());
        if (body != null) out.add(new LicenseData(body, number));
    }

    private boolean professionalExists(ParsedRow row) {
        Person person = personService.findByDocument(row.idType(), row.idNumber()).orElse(null);
        if (person == null) return false;
        Long professionId = row.professions().getFirst().getId();
        return allyRepository.findByHolderPersonIdAndSegment(person.getId(), Segment.B2P).stream()
                .filter(a -> a.getRegistrationStatus() != Ally.RegistrationStatus.REJECTED)
                .filter(a -> a.getCity() != null && Objects.equals(a.getCity().getId(), row.city().getId()))
                .anyMatch(a -> a.getProfessions().stream().anyMatch(p -> Objects.equals(p.getId(), professionId)));
    }

    // ─── Creation ───────────────────────────────────────────────────────────

    private void create(ParsedRow r) {
        boolean b2b = r.segment() == Segment.B2B;
        AllyHolderPersonRequest holder = r.idNumber() == null ? null
                : new AllyHolderPersonRequest(r.idType(), r.idNumber(), r.firstName(), r.lastName(), null,
                        r.mobile(), r.email());
        AllyCreateRequest req = new AllyCreateRequest(
                r.name(), List.of(r.allyType().getUuid()), r.taxType(), r.taxNumber(),
                r.email(), r.phone() != null ? r.phone() : r.mobile(), r.website(), r.mobile(),
                r.instagram(), r.facebook(), r.address(), r.city().getUuid(), r.mapsUrl(), null,
                null, r.description(), null, false, null,
                r.segment(), holder,
                null, b2b ? null : r.hostName(), b2b ? null : r.officeNumber(),
                b2b ? null : r.appointmentPhone(), b2b ? null : r.assistantName(),
                b2b ? r.commercialZone() : null, b2b ? r.tradeName() : null, b2b ? r.foundedYear() : null,
                b2b ? r.cashRegisters() : null, b2b ? r.branches() : null, b2b ? r.hasPos() : null,
                b2b ? r.position() : null,
                r.promoter() != null ? r.promoter().getUuid() : null,
                // No cédula → no person: keep the contact name for the invitation.
                holder == null ? (r.firstName() + " " + r.lastName()).trim() : null, null, null,
                r.professions().isEmpty() ? null : r.professions().stream().map(Profession::getUuid).toList());
        AllyDetailDto created = alliesService.createRequest(req, Ally.RegistrationChannel.IMPORT, r.benefit());
        Ally ally = allyRepository.findByUuid(created.uuid())
                .orElseThrow(() -> new IllegalStateException("ally just imported not found"));

        for (OpeningHoursParser.Range range : r.hours()) {
            AllyOpeningHour hour = new AllyOpeningHour();
            hour.setAlly(ally);
            hour.setDayOfWeek(range.dayOfWeek());
            hour.setOpensAt(range.opensAt());
            hour.setClosesAt(range.closesAt());
            openingHourRepository.save(hour);
        }
        Person person = ally.getHolderPerson();
        if (person != null) {
            for (LicenseData l : r.licenses()) {
                if (licenseRepository.existsByLicensingBody_IdAndLicenseNumberIgnoreCaseAndActiveTrue(
                        l.body().getId(), l.number())) {
                    continue;  // already on file (same professional imported before)
                }
                PersonProfessionalLicense license = new PersonProfessionalLicense();
                license.setPerson(person);
                license.setLicensingBody(l.body());
                license.setLicenseNumber(l.number());
                licenseRepository.save(license);
            }
        }
    }

    // ─── Catalogs ───────────────────────────────────────────────────────────

    private record Catalogs(Map<String, AllyType> allyTypes, Map<String, Profession> professions,
                            Map<String, State> states, Map<Long, Map<String, City>> cities,
                            Map<String, LicensingBody> licensingBodies) {}

    private Catalogs loadCatalogs() {
        Map<String, AllyType> types = new HashMap<>();
        for (AllyType t : allyTypeRepository.findAllByActiveTrueOrderByName()) index(types, t, t.getCode(), t.getName());
        Map<String, Profession> professions = new HashMap<>();
        for (Profession p : professionRepository.findAllByActiveTrueOrderByName()) index(professions, p, p.getCode(), p.getName());
        Map<String, State> states = new HashMap<>();
        for (State s : stateRepository.findAllByActiveTrueOrderByName()) index(states, s, s.getCode(), s.getName());
        Map<Long, Map<String, City>> cities = new HashMap<>();
        for (City c : cityRepository.findAllByActiveTrueOrderByName()) {
            cities.computeIfAbsent(c.getState().getId(), k -> new HashMap<>())
                    .putIfAbsent(AllyImportColumn.normalize(c.getName()), c);
        }
        Map<String, LicensingBody> bodies = new HashMap<>();
        for (LicensingBody b : licensingBodyRepository.findAllByActiveTrueOrderByName()) index(bodies, b, b.getCode(), b.getName());
        return new Catalogs(types, professions, states, cities, bodies);
    }

    private static <T> void index(Map<String, T> map, T value, String code, String name) {
        if (code != null) map.putIfAbsent(AllyImportColumn.normalize(code.replace('_', ' ')), value);
        if (name != null) map.putIfAbsent(AllyImportColumn.normalize(name), value);
    }

    private static <T> T lookup(RowContext ctx, AllyImportColumn column, Map<String, T> catalog) {
        String text = ctx.value(column);
        if (text == null) return null;
        T found = catalog.get(AllyImportColumn.normalize(text.replace('_', ' ')));
        if (found == null) ctx.error(column, "not_found", text);
        return found;
    }

    // ─── Value parsing ──────────────────────────────────────────────────────

    static Segment parseSegment(String text) {
        if (text == null) return null;
        String n = AllyImportColumn.normalize(text);
        if (n.equals("comercial") || n.equals("b2b") || n.startsWith("comercial ") || n.equals("commercial")) return Segment.B2B;
        if (n.equals("profesional") || n.equals("b2p") || n.startsWith("profesional ") || n.equals("professional")) return Segment.B2P;
        return null;
    }

    private static Boolean parseYesNo(RowContext ctx, AllyImportColumn column) {
        String text = ctx.value(column);
        if (text == null) return null;
        String n = AllyImportColumn.normalize(text);
        if (List.of("si", "s", "yes", "y", "true", "1", "x").contains(n)) return true;
        if (List.of("no", "n", "false", "0").contains(n)) return false;
        ctx.error(column, "invalid_value", text);
        return null;
    }

    private static Integer parseInt(RowContext ctx, AllyImportColumn column) {
        String text = ctx.value(column);
        if (text == null) return null;
        try {
            int v = Integer.parseInt(text.replace(".", "").replace(",", "").trim());
            if (v < 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException ex) {
            ctx.error(column, "invalid_value", text);
            return null;
        }
    }

    private static Short parseShort(RowContext ctx, AllyImportColumn column, int min, int max) {
        Integer v = parseInt(ctx, column);
        if (v == null) return null;
        if (v < min || v > max) {
            ctx.error(column, "invalid_value", String.valueOf(v));
            return null;
        }
        return v.shortValue();
    }

    private static String digitsOnly(String text) {
        return text == null ? null : text.replaceAll("[\\s.\\-]", "");
    }

    private static String upper(String text) {
        return text == null ? null : text.trim().toUpperCase(Locale.ROOT);
    }

    private static String lower(String text) {
        return text == null ? null : text.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripAt(String text) {
        return text == null ? null : text.replaceFirst("^@", "");
    }

    // ─── Row context ────────────────────────────────────────────────────────

    /** Reads one row's cells by column and collects its errors with localized messages. */
    private final class RowContext {
        private final TabularFileReader.Line line;
        private final Map<AllyImportColumn, Integer> columns;
        private final List<RowError> errors;
        private final Locale locale;
        private boolean failed;

        RowContext(TabularFileReader.Line line, Map<AllyImportColumn, Integer> columns, List<RowError> errors,
                   Locale locale) {
            this.line = line;
            this.columns = columns;
            this.errors = errors;
            this.locale = locale;
        }

        String value(AllyImportColumn column) {
            Integer index = columns.get(column);
            if (index == null) return null;
            String v = line.cell(index);
            return v == null || v.isBlank() ? null : v.trim();
        }

        void error(AllyImportColumn column, String code, Object... args) {
            failed = true;
            Object[] all = new Object[args.length + 1];
            all[0] = column.header();
            System.arraycopy(args, 0, all, 1, args.length);
            String message = messageSource.getMessage("ally_import." + code, all, code, locale);
            errors.add(new RowError(line.number(), column.header(), code, message));
        }

        boolean failed() {
            return failed;
        }
    }

    private record LicenseData(LicensingBody body, String number) {}

    private record ParsedRow(Segment segment, String name, String tradeName, String taxType, String taxNumber,
                             AllyType allyType, List<Profession> professions, String firstName, String lastName,
                             String idType, String idNumber, String position, String mobile, String phone,
                             String email, City city, String address, String mapsUrl, String instagram,
                             String facebook, String website, String commercialZone, Short foundedYear,
                             Integer branches, Integer cashRegisters, Boolean hasPos, String hostName,
                             String officeNumber, String appointmentPhone, String assistantName,
                             List<LicenseData> licenses, List<OpeningHoursParser.Range> hours, String benefit,
                             Promoter promoter, String description) {}
}
