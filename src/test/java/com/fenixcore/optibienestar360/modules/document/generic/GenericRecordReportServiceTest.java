package com.fenixcore.optibienestar360.modules.document.generic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericTableModel;
import com.fenixcore.optibienestar360.modules.document.generic.service.*;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperFormat;
import com.fenixcore.optibienestar360.modules.document.service.RenderedDocument;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GenericRecordReportServiceTest {

    private GenericEntityExtractorService extractorService;
    private GenericHtmlPdfService htmlPdfService;
    private GenericXlsxExporterService xlsxExporterService;
    private GenericRecordReportService recordReportService;

    public static class DummyBeneficiary {
        private String nombre;
        private String parentesco;
        private Integer edad;

        public DummyBeneficiary(String nombre, String parentesco, Integer edad) {
            this.nombre = nombre;
            this.parentesco = parentesco;
            this.edad = edad;
        }

        public String getNombre() { return nombre; }
        public String getParentesco() { return parentesco; }
        public Integer getEdad() { return edad; }
    }

    public static class DummyMember {
        private String cedula;
        private String nombreCompleto;
        private BigDecimal montoCuota;
        private Boolean esActivo;
        private LocalDate fechaAfiliacion;
        private String secretToken;
        private List<DummyBeneficiary> beneficiarios;

        public DummyMember(String nombreCompleto, String cedula, BigDecimal montoCuota, Boolean esActivo, LocalDate fechaAfiliacion, String secretToken, List<DummyBeneficiary> beneficiarios) {
            this.nombreCompleto = nombreCompleto;
            this.cedula = cedula;
            this.montoCuota = montoCuota;
            this.esActivo = esActivo;
            this.fechaAfiliacion = fechaAfiliacion;
            this.secretToken = secretToken;
            this.beneficiarios = beneficiarios;
        }

        public String getNombreCompleto() { return nombreCompleto; }
        public String getCedula() { return cedula; }
        public BigDecimal getMontoCuota() { return montoCuota; }
        public Boolean getEsActivo() { return esActivo; }
        public LocalDate getFechaAfiliacion() { return fechaAfiliacion; }
        public String getSecretToken() { return secretToken; }
        public List<DummyBeneficiary> getBeneficiarios() { return beneficiarios; }
    }

    public static class DummyAgreement {
        private String agreementType;
        private LocalDate startDate;
        private String status;
        private DummyAlly ally;

        public DummyAgreement(String agreementType, LocalDate startDate, String status, DummyAlly ally) {
            this.agreementType = agreementType;
            this.startDate = startDate;
            this.status = status;
            this.ally = ally;
        }

        public String getAgreementType() { return agreementType; }
        public LocalDate getStartDate() { return startDate; }
        public String getStatus() { return status; }
        public DummyAlly getAlly() { return ally; }
    }

    public static class DummyService {
        private String name;
        private BigDecimal priceUsd;
        private DummyAlly ally;

        public DummyService(String name, BigDecimal priceUsd, DummyAlly ally) {
            this.name = name;
            this.priceUsd = priceUsd;
            this.ally = ally;
        }

        public String getName() { return name; }
        public BigDecimal getPriceUsd() { return priceUsd; }
        public DummyAlly getAlly() { return ally; }
    }

    public static class DummyAlly {
        private String name;
        private AllyType allyType;
        private City city;
        private String taxDocumentType;
        private String taxDocumentNumber;
        private String phone;
        private String email;
        private List<DummyService> services;
        private List<DummyAgreement> agreements;

        public DummyAlly(String name, AllyType allyType, City city, String taxDocumentType, String taxDocumentNumber, String phone, String email) {
            this(name, allyType, city, taxDocumentType, taxDocumentNumber, phone, email, null, null);
        }

        public DummyAlly(String name, AllyType allyType, City city, String taxDocumentType, String taxDocumentNumber, String phone, String email, List<DummyService> services, List<DummyAgreement> agreements) {
            this.name = name;
            this.allyType = allyType;
            this.city = city;
            this.taxDocumentType = taxDocumentType;
            this.taxDocumentNumber = taxDocumentNumber;
            this.phone = phone;
            this.email = email;
            this.services = services;
            this.agreements = agreements;
        }

        public String getName() { return name; }
        public AllyType getAllyType() { return allyType; }
        public City getCity() { return city; }
        public String getTaxDocumentType() { return taxDocumentType; }
        public String getTaxDocumentNumber() { return taxDocumentNumber; }
        public String getPhone() { return phone; }
        public String getEmail() { return email; }
        public List<DummyService> getServices() { return services; }
        public List<DummyAgreement> getAgreements() { return agreements; }
    }

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        extractorService = new GenericEntityExtractorService(mapper, null);

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        htmlPdfService = new GenericHtmlPdfService(templateEngine, null);
        xlsxExporterService = new GenericXlsxExporterService();
        recordReportService = new GenericRecordReportService(extractorService, htmlPdfService, xlsxExporterService, null);
    }

    @Test
    @DisplayName("Should extract DTO metadata, format amounts in es-VE and omit sensitive fields")
    void testExtractorService() {
        DummyMember dummy = new DummyMember(
                "Carlos Mendoza",
                "V-18452109",
                new BigDecimal("1250.50"),
                true,
                LocalDate.of(2026, 5, 10),
                "SECRET_TOKEN_12345",
                List.of(new DummyBeneficiary("Maria Mendoza", "HIJA", 12))
        );

        GenericRecordModel model = extractorService.extractModel(dummy, "Ficha Afiliado", "Prueba Subtítulo", "AFF-100", "Admin");

        assertEquals("Ficha Afiliado", model.title());
        assertEquals("AFF-100", model.identifier());

        Map<String, String> fields = model.fields();
        assertTrue(fields.containsKey("Nombre Completo"));
        assertEquals("Carlos Mendoza", fields.get("Nombre Completo"));
        assertTrue(fields.get("Monto Cuota").contains("1.250,50"));
        assertEquals("Sí", fields.get("Activo"));

        // Verify secret token was excluded
        assertFalse(fields.containsKey("Secret Token"));
        assertFalse(fields.containsKey("secretToken"));

        // Verify secondary detail section
        assertEquals(1, model.detailSections().size());
        assertEquals("Beneficiarios", model.detailSections().get(0).title());
    }

    @Test
    @DisplayName("Should sort record fields: document/code first, names second, details in middle, active/status at end")
    void testFieldsSortedByBusinessPriority() {
        DummyMember dummy = new DummyMember(
                "Carlos Mendoza",
                "V-18452109",
                new BigDecimal("1250.50"),
                true,
                LocalDate.of(2026, 5, 10),
                "SECRET_TOKEN_12345",
                null
        );

        GenericRecordModel model = extractorService.extractModel(dummy, "Ficha Afiliado", "Subtítulo", "AFF-100", "Admin");

        List<String> keyList = new ArrayList<>(model.fields().keySet());

        // 1. First field should be Document / Cedula
        assertEquals("Cedula", keyList.get(0));
        // 2. Second field should be Full Name
        assertEquals("Nombre Completo", keyList.get(1));
        // 3. Last field should be Active
        assertEquals("Activo", keyList.get(keyList.size() - 1));
    }

    @Test
    @DisplayName("Should translate Plan Type field and its value")
    void testPlanTypeTranslation() {
        Plan plan = new Plan();
        plan.setCode("IND-01");
        plan.setName("Plan Individual");
        plan.setType(Plan.PlanType.INDIVIDUAL);
        plan.setMonthlyFee(new BigDecimal("15.00"));
        plan.setInscriptionFee(new BigDecimal("10.00"));
        plan.setStatus("ACTIVE");

        GenericRecordModel model = extractorService.extractModel(plan, "Ficha de Plan", "Detalle", "IND-01", "Admin");
        Map<String, String> fields = model.fields();

        assertTrue(fields.containsKey("Tipo"), "Should contain Spanish label 'Tipo'");
        assertEquals("Individual", fields.get("Tipo"), "PlanType value should be 'Individual'");
    }

    @Test
    @DisplayName("Should translate Promoter User and Person fields to readable values")
    void testPromoterUserAndPersonTranslation() {
        Person person = new Person();
        person.setFirstName("Carlos");
        person.setLastName("Pérez");
        person.setDocumentType("V");
        person.setDocumentNumber("12345678");

        User user = new User();
        user.setEmail("carlos.perez@optibienestar.com");
        user.setPerson(person);

        PromoterType promoterType = new PromoterType();
        promoterType.setName("Comercial Senior");
        promoterType.setCode("SENIOR");

        Promoter promoter = new Promoter();
        promoter.setReferralCode("PROM-001");
        promoter.setDisplayName("Carlos Pérez");
        promoter.setUser(user);
        promoter.setPerson(person);
        promoter.setPromoterType(promoterType);
        promoter.setEmail("carlos.perez@optibienestar.com");
        promoter.setPhone("+58 414 1234567");
        promoter.setStatus("ACTIVE");

        GenericRecordModel model = extractorService.extractModel(promoter, "Ficha de Promotor", "Detalle", "PROM-001", "Admin");
        Map<String, String> fields = model.fields();

        // 1. Should translate 'user' to 'Usuario' and show email or name
        assertTrue(fields.containsKey("Usuario"), "Should contain label 'Usuario'");
        assertEquals("carlos.perez@optibienestar.com", fields.get("Usuario"));

        // 2. Should translate 'person' to 'Persona' and show full name
        assertTrue(fields.containsKey("Persona"), "Should contain label 'Persona'");
        assertEquals("Carlos Pérez", fields.get("Persona"));

        // 3. Should translate 'promoterType' to 'Tipo de Promotor'
        assertTrue(fields.containsKey("Tipo de Promotor"));
        assertEquals("Comercial Senior", fields.get("Tipo de Promotor"));
    }

    @Test
    @DisplayName("Should extract related entity names like AllyType and City and translate column headers")
    void testExtractorServiceWithRelatedEntities() {
        AllyType tipoClinica = new AllyType();
        tipoClinica.setName("Clínica");
        tipoClinica.setCode("CLINICA");

        City caracas = new City();
        caracas.setName("Caracas");

        DummyAlly ally = new DummyAlly(
                "Centro Oftalmológico Vicente",
                tipoClinica,
                caracas,
                "J",
                "123456789",
                "+58 212 5550000",
                "contacto@opticavicente.com"
        );

        GenericRecordModel model = extractorService.extractModel(ally, "Ficha de Aliado", "Detalle de Registro", "ALLY-01", "Admin");

        Map<String, String> fields = model.fields();

        // 1. Should translate 'allyType' to 'Tipo de Aliado'
        assertTrue(fields.containsKey("Tipo de Aliado"), "Should contain Spanish label 'Tipo de Aliado'");
        // 2. Value should be the ally type name ('Clínica')
        assertEquals("Clínica", fields.get("Tipo de Aliado"), "Value of Tipo de Aliado should be 'Clínica'");

        // 3. Should translate 'city' to 'Ciudad' and value should be 'Caracas'
        assertTrue(fields.containsKey("Ciudad"));
        assertEquals("Caracas", fields.get("Ciudad"));

        // 4. Should translate other fields
        assertTrue(fields.containsKey("Nombre"));
        assertEquals("Centro Oftalmológico Vicente", fields.get("Nombre"));
        assertTrue(fields.containsKey("Correo Electrónico"));
        assertEquals("contacto@opticavicente.com", fields.get("Correo Electrónico"));
    }

    @Test
    @DisplayName("Should omit parent column (Ally) in child tables of services")
    void testExcludeParentReferenceFromChildTables() {
        DummyAlly ally = new DummyAlly(
                "Centro Oftalmológico Vicente",
                null,
                null,
                "J",
                "123456789",
                "+58 212 5550000",
                "contacto@opticavicente.com"
        );

        DummyService service1 = new DummyService("Consulta General", new BigDecimal("25.00"), ally);
        DummyService service2 = new DummyService("Examen de Fondo", new BigDecimal("45.00"), ally);

        DummyAlly allyWithServices = new DummyAlly(
                "Centro Oftalmológico Vicente",
                null,
                null,
                "J",
                "123456789",
                "+58 212 5550000",
                "contacto@opticavicente.com",
                List.of(service1, service2),
                null
        );

        GenericRecordModel model = extractorService.extractModel(allyWithServices, "Ficha de Aliado", "Detalle", "ALLY-01", "Admin");

        assertEquals(1, model.detailSections().size());
        GenericRecordModel.DetailSection section = model.detailSections().get(0);
        assertEquals("Servicios Ofrecidos", section.title());

        // Verify headers contain Name and Price (USD), but NOT 'Aliado' nor 'Ally'
        assertTrue(section.headers().contains("Nombre"));
        assertTrue(section.headers().contains("Precio (USD)"));
        assertFalse(section.headers().contains("Aliado"), "Should not show parent reference column 'Aliado'");
        assertFalse(section.headers().contains("Ally"), "Should not show parent reference column 'Ally'");
    }

    @Test
    @DisplayName("Should dynamically translate agreement status (ACTIVE -> Activo) and types in child tables")
    void testTranslateAgreementStatusAndTypeDynamically() {
        DummyAlly ally = new DummyAlly(
                "Centro Oftalmológico Vicente",
                null,
                null,
                "J",
                "123456789",
                "+58 212 5550000",
                "contacto@opticavicente.com"
        );

        DummyAgreement agr1 = new DummyAgreement("COMMERCIAL", LocalDate.of(2026, 1, 15), "ACTIVE", ally);
        DummyAgreement agr2 = new DummyAgreement("EXCLUSIVITY", LocalDate.of(2025, 6, 1), "TERMINATED", ally);

        DummyAlly allyWithAgreements = new DummyAlly(
                "Centro Oftalmológico Vicente",
                null,
                null,
                "J",
                "123456789",
                "+58 212 5550000",
                "contacto@opticavicente.com",
                null,
                List.of(agr1, agr2)
        );

        GenericRecordModel model = extractorService.extractModel(allyWithAgreements, "Ficha de Aliado", "Detalle", "ALLY-01", "Admin");

        assertEquals(1, model.detailSections().size());
        GenericRecordModel.DetailSection section = model.detailSections().get(0);
        assertEquals("Acuerdos Comerciales", section.title());

        int typeIndex = section.headers().indexOf("Tipo de Acuerdo");
        int statusIndex = section.headers().indexOf("Estado");

        assertTrue(typeIndex >= 0);
        assertTrue(statusIndex >= 0);

        // Row 1: COMMERCIAL -> Comercial, ACTIVE -> Activo
        assertEquals("Comercial", section.rows().get(0).get(typeIndex));
        assertEquals("Activo", section.rows().get(0).get(statusIndex));

        // Row 2: EXCLUSIVITY -> Exclusividad, TERMINATED -> Terminado
        assertEquals("Exclusividad", section.rows().get(1).get(typeIndex));
        assertEquals("Terminado", section.rows().get(1).get(statusIndex));
    }

    @Test
    @DisplayName("Should generate a valid PDF (%PDF) for generic payloads")
    void testGenerateGenericPdfDocument() {
        DummyMember dummy = new DummyMember(
                "Carlos Mendoza",
                "V-18452109",
                new BigDecimal("1250.50"),
                true,
                LocalDate.of(2026, 5, 10),
                "SECRET_TOKEN",
                List.of(new DummyBeneficiary("Maria Mendoza", "HIJA", 12))
        );

        RenderedDocument pdfDoc = recordReportService.generateGenericRecordDocument(
                dummy,
                "Ficha de Afiliado",
                "Detalle de Registro",
                "AFF-9001",
                "Operador Test",
                JasperFormat.PDF
        );

        assertNotNull(pdfDoc);
        assertEquals("application/pdf", pdfDoc.contentType());
        assertTrue(pdfDoc.content().length > 0);

        String pdfHeader = new String(pdfDoc.content(), 0, 4, StandardCharsets.ISO_8859_1);
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    @DisplayName("Should generate a valid XLSX (PK zip bytes) for generic payloads")
    void testGenerateGenericXlsxDocument() {
        Map<String, Object> genericMap = Map.of(
                "nombreEmpresa", "OptiBienestar 360",
                "montoFacturado", new BigDecimal("5400.00"),
                "fechaCobro", LocalDate.of(2026, 8, 1)
        );

        RenderedDocument xlsxDoc = recordReportService.generateGenericRecordDocument(
                genericMap,
                "Reporte Genérico",
                "Exportación a Excel",
                "REP-88",
                "Admin",
                JasperFormat.XLSX
        );

        assertNotNull(xlsxDoc);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxDoc.contentType());
        assertTrue(xlsxDoc.content().length > 0);

        assertEquals((byte) 'P', xlsxDoc.content()[0]);
        assertEquals((byte) 'K', xlsxDoc.content()[1]);
    }

    @Test
    @DisplayName("Should generate a table list with headers sorted by business priority")
    void testExtractTableModelWithRelatedEntities() {
        AllyType tipoClinica = new AllyType();
        tipoClinica.setName("Clínica");
        tipoClinica.setCode("CLINICA");

        City caracas = new City();
        caracas.setName("Caracas");

        DummyAlly ally1 = new DummyAlly("Centro 1", tipoClinica, caracas, "J", "111", "0212", "c1@test.com");
        DummyAlly ally2 = new DummyAlly("Centro 2", tipoClinica, caracas, "J", "222", "0212", "c2@test.com");

        GenericTableModel tableModel = extractorService.extractTableModel(
                List.of(ally1, ally2), "Listado de Aliados", "Subtítulo", "Admin"
        );

        assertNotNull(tableModel);
        assertEquals(2, tableModel.totalRecords());

        // 1. Business headers should be present
        assertTrue(tableModel.headers().contains("Nombre"));
        assertTrue(tableModel.headers().contains("Tipo de Aliado"));
        assertTrue(tableModel.headers().contains("Ciudad"));

        int allyTypeIndex = tableModel.headers().indexOf("Tipo de Aliado");
        int cityIndex = tableModel.headers().indexOf("Ciudad");

        assertEquals("Clínica", tableModel.rows().get(0).get(allyTypeIndex));
        assertEquals("Caracas", tableModel.rows().get(0).get(cityIndex));
    }

    @Test
    @DisplayName("Should extract Promoter table model with business columns (code, display name, promoter type, system, total referrals) and omit raw entity columns")
    void testExtractTableModelForPromoters() {
        // System promoter (INSTITUCION)
        Promoter systemPromoter = new Promoter();
        systemPromoter.setReferralCode("INSTITUCION");
        systemPromoter.setDisplayName("Centro Óptico Vicente — Administración");
        systemPromoter.setDescription("Promotor del sistema...");
        systemPromoter.setSystem(true);
        systemPromoter.setTotalReferrals(0);
        systemPromoter.setStatus("ACTIVE");
        systemPromoter.setActive(true);

        // Human promoter
        Person person = new Person();
        person.setFirstName("Carlos");
        person.setLastName("Pérez");
        person.setDocumentType("V");
        person.setDocumentNumber("12345678");

        User user = new User();
        user.setEmail("carlos.perez@optibienestar.com");
        user.setPerson(person);

        PromoterType promoterType = new PromoterType();
        promoterType.setName("Comercial Senior");
        promoterType.setCode("SENIOR");

        Promoter humanPromoter = new Promoter();
        humanPromoter.setReferralCode("PROM-001");
        humanPromoter.setDisplayName("Carlos Pérez");
        humanPromoter.setUser(user);
        humanPromoter.setPerson(person);
        humanPromoter.setPromoterType(promoterType);
        humanPromoter.setEmail("carlos.perez@optibienestar.com");
        humanPromoter.setPhone("+58 414 1234567");
        humanPromoter.setTotalReferrals(12);
        humanPromoter.setSystem(false);
        humanPromoter.setStatus("ACTIVE");
        humanPromoter.setActive(true);

        GenericTableModel tableModel = extractorService.extractTableModel(
                List.of(systemPromoter, humanPromoter), "Listado de Promotores", "Subtítulo", "Admin"
        );

        assertNotNull(tableModel);
        assertEquals(2, tableModel.totalRecords());

        List<String> headers = tableModel.headers();

        // 1. Business columns must be present and ordered
        assertTrue(headers.contains("Código de Referido"), "Headers must contain 'Código de Referido'");
        assertTrue(headers.contains("Nombre a Mostrar"), "Headers must contain 'Nombre a Mostrar'");
        assertTrue(headers.contains("Tipo de Promotor"), "Headers must contain 'Tipo de Promotor'");
        assertTrue(headers.contains("Correo Electrónico"), "Headers must contain 'Correo Electrónico'");
        assertTrue(headers.contains("Teléfono"), "Headers must contain 'Teléfono'");
        assertTrue(headers.contains("Sistema"), "Headers must contain 'Sistema'");
        assertTrue(headers.contains("Total de Referidos"), "Headers must contain 'Total de Referidos'");
        assertTrue(headers.contains("Estado"), "Headers must contain 'Estado'");
        assertTrue(headers.contains("Activo"), "Headers must contain 'Activo'");

        // 2. Raw entity object headers like "Usuario" and "Persona" should NOT be table columns
        assertFalse(headers.contains("Usuario"), "Raw entity relation 'Usuario' should not be a table column");
        assertFalse(headers.contains("Persona"), "Raw entity relation 'Persona' should not be a table column");

        // 3. Row values for INSTITUCION
        int codeIdx = headers.indexOf("Código de Referido");
        int nameIdx = headers.indexOf("Nombre a Mostrar");
        int systemIdx = headers.indexOf("Sistema");
        int referralsIdx = headers.indexOf("Total de Referidos");

        assertEquals("INSTITUCION", tableModel.rows().get(0).get(codeIdx));
        assertEquals("Centro Óptico Vicente — Administración", tableModel.rows().get(0).get(nameIdx));
        assertEquals("Sí", tableModel.rows().get(0).get(systemIdx));
        assertEquals("0", tableModel.rows().get(0).get(referralsIdx));

        // 4. Row values for Human Promoter
        int typeIdx = headers.indexOf("Tipo de Promotor");
        assertEquals("PROM-001", tableModel.rows().get(1).get(codeIdx));
        assertEquals("Carlos Pérez", tableModel.rows().get(1).get(nameIdx));
        assertEquals("Comercial Senior", tableModel.rows().get(1).get(typeIdx));
        assertEquals("No", tableModel.rows().get(1).get(systemIdx));
        assertEquals("12", tableModel.rows().get(1).get(referralsIdx));
    }

    @Test
    @DisplayName("Should generate valid table PDF without error")
    void testGenerateTablePdfDocument() {
        DummyAlly ally1 = new DummyAlly("Centro 1", null, null, "J", "111", "0212", "c1@test.com");
        RenderedDocument doc = recordReportService.generateGenericTableDocument(
                List.of(ally1), "Listado de Aliados", "Subtítulo", "Admin", JasperFormat.PDF
        );
        assertNotNull(doc);
        assertEquals("application/pdf", doc.contentType());
        assertTrue(doc.content().length > 0);
    }

    @Test
    @DisplayName("Should generate empty table XLSX with localized message when MessageSource is provided")
    void testGenerateEmptyTableXlsxLocalized() {
        org.springframework.context.MessageSource mockMsg = org.mockito.Mockito.mock(org.springframework.context.MessageSource.class);
        org.mockito.Mockito.when(mockMsg.getMessage(org.mockito.ArgumentMatchers.eq("document.table_list.no_data"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(java.util.Locale.forLanguageTag("es"))))
                .thenReturn("No se encontraron registros para mostrar");
        org.mockito.Mockito.when(mockMsg.getMessage(org.mockito.ArgumentMatchers.eq("document.table_list.no_data"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(java.util.Locale.ENGLISH)))
                .thenReturn("No records found to display");

        GenericXlsxExporterService exporter = new GenericXlsxExporterService(mockMsg);
        GenericTableModel emptyModel = new GenericTableModel(
                "Test Title", "Test Subtitle", "2026-09-25", "Admin", List.of("Col1", "Col2"), List.of(), 0
        );

        // Under Spanish locale
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.forLanguageTag("es"));
        byte[] esBytes = exporter.generateTableXlsx(emptyModel);
        assertNotNull(esBytes);
        assertTrue(esBytes.length > 0);

        // Under English locale
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.ENGLISH);
        byte[] enBytes = exporter.generateTableXlsx(emptyModel);
        assertNotNull(enBytes);
        assertTrue(enBytes.length > 0);

        // Reset locale
        org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
    }
}
