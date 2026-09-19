package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericTableModel;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GenericEntityExtractorService {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final Locale venezuelaLocale = Locale.of("es", "VE");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private static final Set<String> IGNORED_EXACT_FIELDS = Set.of(
            "password", "token", "secret", "deletedat", "version", "updatedat", "hash", "salt", "id",
            "createdby", "updatedby", "logourl", "class", "hibernatelazyinitializer", "handler",
            "passwordhash", "refreshtoken", "accesstoken", "uuid"
    );

    // Fallback dictionary of Spanish labels for common domain fields and columns
    private static final Map<String, String> SPANISH_FIELD_LABELS = new LinkedHashMap<>();
    static {
        SPANISH_FIELD_LABELS.put("ally", "Aliado");
        SPANISH_FIELD_LABELS.put("allies", "Aliados");
        SPANISH_FIELD_LABELS.put("allytype", "Tipo de Aliado");
        SPANISH_FIELD_LABELS.put("allyrole", "Rol en Aliado");
        SPANISH_FIELD_LABELS.put("allyname", "Nombre del Aliado");
        SPANISH_FIELD_LABELS.put("taxdocumenttype", "Tipo de Documento Tributario");
        SPANISH_FIELD_LABELS.put("taxdocumentnumber", "Número de RIF / Documento");
        SPANISH_FIELD_LABELS.put("rif", "RIF");
        SPANISH_FIELD_LABELS.put("name", "Nombre");
        SPANISH_FIELD_LABELS.put("firstname", "Primer Nombre");
        SPANISH_FIELD_LABELS.put("middlename", "Segundo Nombre");
        SPANISH_FIELD_LABELS.put("lastname", "Primer Apellido");
        SPANISH_FIELD_LABELS.put("secondlastname", "Segundo Apellido");
        SPANISH_FIELD_LABELS.put("fullname", "Nombre Completo");
        SPANISH_FIELD_LABELS.put("displayname", "Nombre a Mostrar");
        SPANISH_FIELD_LABELS.put("email", "Correo Electrónico");
        SPANISH_FIELD_LABELS.put("phone", "Teléfono");
        SPANISH_FIELD_LABELS.put("landlinephone", "Teléfono Fijo");
        SPANISH_FIELD_LABELS.put("website", "Sitio Web");
        SPANISH_FIELD_LABELS.put("address", "Dirección");
        SPANISH_FIELD_LABELS.put("city", "Ciudad");
        SPANISH_FIELD_LABELS.put("state", "Estado");
        SPANISH_FIELD_LABELS.put("country", "País");
        SPANISH_FIELD_LABELS.put("gender", "Género");
        SPANISH_FIELD_LABELS.put("maritalstatus", "Estado Civil");
        SPANISH_FIELD_LABELS.put("occupation", "Ocupación");
        SPANISH_FIELD_LABELS.put("birthdate", "Fecha de Nacimiento");
        SPANISH_FIELD_LABELS.put("birthplace", "Lugar de Nacimiento");
        SPANISH_FIELD_LABELS.put("numberofchildren", "Número de Hijos");
        SPANISH_FIELD_LABELS.put("spousename", "Nombre del Cónyuge");
        SPANISH_FIELD_LABELS.put("employername", "Empresa / Empleador");
        SPANISH_FIELD_LABELS.put("jobposition", "Cargo / Puesto");
        SPANISH_FIELD_LABELS.put("employeraddress", "Dirección del Empleador");
        SPANISH_FIELD_LABELS.put("enrolledat", "Fecha de Inscripción");
        SPANISH_FIELD_LABELS.put("joinedat", "Fecha de Ingreso");
        SPANISH_FIELD_LABELS.put("expiresat", "Fecha de Vencimiento");
        SPANISH_FIELD_LABELS.put("nextduedate", "Próxima Fecha de Pago");
        SPANISH_FIELD_LABELS.put("lastpaidthrough", "Pagado Hasta");
        SPANISH_FIELD_LABELS.put("inscriptionfee", "Cuota de Inscripción");
        SPANISH_FIELD_LABELS.put("monthlyfee", "Cuota Mensual");
        SPANISH_FIELD_LABELS.put("graceperioddays", "Días de Gracia");
        SPANISH_FIELD_LABELS.put("status", "Estado");
        SPANISH_FIELD_LABELS.put("published", "Publicado");
        SPANISH_FIELD_LABELS.put("ispublished", "Publicado");
        SPANISH_FIELD_LABELS.put("publishedat", "Fecha de Publicación");
        SPANISH_FIELD_LABELS.put("specialties", "Especialidades Médicas");
        SPANISH_FIELD_LABELS.put("specialty", "Especialidad Médica");
        SPANISH_FIELD_LABELS.put("medicalspecialty", "Especialidad Médica");
        SPANISH_FIELD_LABELS.put("services", "Servicios Ofrecidos");
        SPANISH_FIELD_LABELS.put("servicecategory", "Categoría de Servicio");
        SPANISH_FIELD_LABELS.put("agreements", "Acuerdos Comerciales");
        SPANISH_FIELD_LABELS.put("agreementtype", "Tipo de Acuerdo");
        SPANISH_FIELD_LABELS.put("type", "Tipo");
        SPANISH_FIELD_LABELS.put("user", "Usuario");
        SPANISH_FIELD_LABELS.put("users", "Personal / Usuarios");
        SPANISH_FIELD_LABELS.put("person", "Persona");
        SPANISH_FIELD_LABELS.put("system", "Sistema");
        SPANISH_FIELD_LABELS.put("issystem", "Sistema");
        SPANISH_FIELD_LABELS.put("primary", "Contacto Principal");
        SPANISH_FIELD_LABELS.put("amount", "Monto");
        SPANISH_FIELD_LABELS.put("currency", "Moneda");
        SPANISH_FIELD_LABELS.put("paymentmethod", "Método de Pago");
        SPANISH_FIELD_LABELS.put("referencenumber", "Número de Referencia");
        SPANISH_FIELD_LABELS.put("paymentdate", "Fecha de Pago");
        SPANISH_FIELD_LABELS.put("receivedat", "Fecha de Recepción");
        SPANISH_FIELD_LABELS.put("inscription", "Inscripción");
        SPANISH_FIELD_LABELS.put("appliedperiod", "Período Aplicado");
        SPANISH_FIELD_LABELS.put("promoter", "Promotor");
        SPANISH_FIELD_LABELS.put("currentpromoter", "Promotor Actual");
        SPANISH_FIELD_LABELS.put("currentpromotername", "Promotor Actual");
        SPANISH_FIELD_LABELS.put("referralcode", "Código de Referido");
        SPANISH_FIELD_LABELS.put("totalreferrals", "Total de Referidos");
        SPANISH_FIELD_LABELS.put("totalcommissionpaid", "Total Comisiones Pagadas");
        SPANISH_FIELD_LABELS.put("plan", "Plan");
        SPANISH_FIELD_LABELS.put("plancode", "Código del Plan");
        SPANISH_FIELD_LABELS.put("planname", "Nombre del Plan");
        SPANISH_FIELD_LABELS.put("plantype", "Tipo de Plan");
        SPANISH_FIELD_LABELS.put("member", "Afiliado");
        SPANISH_FIELD_LABELS.put("relationship", "Parentesco");
        SPANISH_FIELD_LABELS.put("extrainscriptionpaid", "Inscripción Extra Pagada");
        SPANISH_FIELD_LABELS.put("beneficiaries", "Beneficiarios");
        SPANISH_FIELD_LABELS.put("includedbeneficiaries", "Beneficiarios Incluidos");
        SPANISH_FIELD_LABELS.put("maxbeneficiaries", "Máximo de Beneficiarios");
        SPANISH_FIELD_LABELS.put("extrabeneficiaryinscriptionfee", "Cuota por Beneficiario Extra");
        SPANISH_FIELD_LABELS.put("bloodtype", "Tipo de Sangre");
        SPANISH_FIELD_LABELS.put("allergies", "Alergias");
        SPANISH_FIELD_LABELS.put("chronicconditions", "Condiciones Crónicas");
        SPANISH_FIELD_LABELS.put("currentmedications", "Medicamentos Actuales");
        SPANISH_FIELD_LABELS.put("emergencycontactname", "Contacto de Emergencia");
        SPANISH_FIELD_LABELS.put("emergencycontactphone", "Teléfono de Emergencia");
        SPANISH_FIELD_LABELS.put("emergencycontactrelationship", "Parentesco del Contacto");
        SPANISH_FIELD_LABELS.put("notes", "Notas");
        SPANISH_FIELD_LABELS.put("adminnotes", "Notas del Administrador");
        SPANISH_FIELD_LABELS.put("code", "Código");
        SPANISH_FIELD_LABELS.put("isocode", "Código ISO");
        SPANISH_FIELD_LABELS.put("priceusd", "Precio (USD)");
        SPANISH_FIELD_LABELS.put("discountpct", "Descuento (%)");
        SPANISH_FIELD_LABELS.put("requiresappointment", "Requiere Cita");
        SPANISH_FIELD_LABELS.put("reviewstatus", "Estado de Revisión");
        SPANISH_FIELD_LABELS.put("terms", "Términos");
        SPANISH_FIELD_LABELS.put("startdate", "Fecha de Inicio");
        SPANISH_FIELD_LABELS.put("enddate", "Fecha de Fin");
        SPANISH_FIELD_LABELS.put("cronexpression", "Expresión Cron");
        SPANISH_FIELD_LABELS.put("timezone", "Zona Horaria");
        SPANISH_FIELD_LABELS.put("lastexecutionat", "Última Ejecución");
        SPANISH_FIELD_LABELS.put("nextexecutionat", "Próxima Ejecución");
        SPANISH_FIELD_LABELS.put("role", "Rol");
        SPANISH_FIELD_LABELS.put("roles", "Roles");
        SPANISH_FIELD_LABELS.put("active", "Activo");
        SPANISH_FIELD_LABELS.put("isactive", "Activo");
        SPANISH_FIELD_LABELS.put("esactivo", "Activo");
        SPANISH_FIELD_LABELS.put("createdat", "Fecha de Registro");
        SPANISH_FIELD_LABELS.put("confirmedat", "Fecha de Confirmación");
        SPANISH_FIELD_LABELS.put("supportfileavailable", "Soporte Adjunto");
        SPANISH_FIELD_LABELS.put("supportfilename", "Nombre del Archivo");
        SPANISH_FIELD_LABELS.put("reviewedat", "Fecha de Revisión");
        SPANISH_FIELD_LABELS.put("reviewreason", "Motivo de Revisión");
        SPANISH_FIELD_LABELS.put("voidreason", "Motivo de Anulación");
        SPANISH_FIELD_LABELS.put("calculationbasis", "Base de Cálculo");
        SPANISH_FIELD_LABELS.put("commissionpct", "Porcentaje de Comisión");
        SPANISH_FIELD_LABELS.put("flatamount", "Monto Fijo");
        SPANISH_FIELD_LABELS.put("tiernamesnapshot", "Tramo de Comisión");
        SPANISH_FIELD_LABELS.put("appliesto", "Aplica A");
        SPANISH_FIELD_LABELS.put("periodstrategy", "Estrategia de Período");
        SPANISH_FIELD_LABELS.put("periodstart", "Inicio del Período");
        SPANISH_FIELD_LABELS.put("periodend", "Fin del Período");
        SPANISH_FIELD_LABELS.put("earnedat", "Fecha Ganada");
        SPANISH_FIELD_LABELS.put("payoutreference", "Referencia de Liquidación");
        SPANISH_FIELD_LABELS.put("paidat", "Fecha de Pago");
        SPANISH_FIELD_LABELS.put("documenttype", "Tipo de Documento");
        SPANISH_FIELD_LABELS.put("documentnumber", "Número de Documento");
        SPANISH_FIELD_LABELS.put("promotertype", "Tipo de Promotor");
        SPANISH_FIELD_LABELS.put("promotertypename", "Tipo de Promotor");
        SPANISH_FIELD_LABELS.put("useremail", "Correo Electrónico");
        SPANISH_FIELD_LABELS.put("personfullname", "Nombre Completo");
        SPANISH_FIELD_LABELS.put("personrif", "RIF / Documento");
        SPANISH_FIELD_LABELS.put("username", "Usuario");
        SPANISH_FIELD_LABELS.put("description", "Descripción");
        SPANISH_FIELD_LABELS.put("direction", "Dirección de Flujo");
        SPANISH_FIELD_LABELS.put("paymenttype", "Categoría de Pago");
        SPANISH_FIELD_LABELS.put("paymentcategory", "Categoría de Pago");
        SPANISH_FIELD_LABELS.put("paymentcategorycode", "Código de Categoría");
        SPANISH_FIELD_LABELS.put("paymentcategoryname", "Categoría de Pago");
        SPANISH_FIELD_LABELS.put("conceptlabel", "Concepto / Detalle");
        SPANISH_FIELD_LABELS.put("membership", "Membresía");
        SPANISH_FIELD_LABELS.put("amountconverted", "Monto Convertido");
        SPANISH_FIELD_LABELS.put("convertedcurrency", "Moneda de Conversión");
        SPANISH_FIELD_LABELS.put("convertedcurrencycode", "Código de Moneda de Conversión");
        SPANISH_FIELD_LABELS.put("currencycode", "Moneda");
        SPANISH_FIELD_LABELS.put("exchangerateused", "Tasa de Cambio Aplicada");
        SPANISH_FIELD_LABELS.put("exchangeratedate", "Fecha de Tasa de Cambio");
        SPANISH_FIELD_LABELS.put("discountamount", "Monto de Descuento");
        SPANISH_FIELD_LABELS.put("discountreason", "Motivo del Descuento");
        SPANISH_FIELD_LABELS.put("discountedby", "Descontado Por");
        SPANISH_FIELD_LABELS.put("discountedat", "Fecha de Descuento");
        SPANISH_FIELD_LABELS.put("reviewedby", "Revisado Por");
        SPANISH_FIELD_LABELS.put("supportfilecontenttype", "Tipo de Archivo");
        SPANISH_FIELD_LABELS.put("supportfilesizebytes", "Tamaño del Archivo");
        SPANISH_FIELD_LABELS.put("payeruseruuid", "Usuario Pagador");
        SPANISH_FIELD_LABELS.put("payer", "Usuario Pagador");
        SPANISH_FIELD_LABELS.put("payeruser", "Usuario Pagador");
        SPANISH_FIELD_LABELS.put("bank", "Banco");
        SPANISH_FIELD_LABELS.put("bankcode", "Código de Banco");
        SPANISH_FIELD_LABELS.put("bankname", "Banco");
        SPANISH_FIELD_LABELS.put("shortname", "Nombre Corto");
        SPANISH_FIELD_LABELS.put("lines", "Líneas de Pago");
        SPANISH_FIELD_LABELS.put("paymentlines", "Líneas de Pago");
        SPANISH_FIELD_LABELS.put("payoutpayment", "Pago de Liquidación");
        SPANISH_FIELD_LABELS.put("payoutpaymentid", "ID de Liquidación");
        SPANISH_FIELD_LABELS.put("exchangerateatearned", "Tasa de Cambio (Ganada)");
        SPANISH_FIELD_LABELS.put("exchangerateatpaid", "Tasa de Cambio (Pagada)");
        SPANISH_FIELD_LABELS.put("origincurrency", "Moneda Origen");
        SPANISH_FIELD_LABELS.put("origincode", "Código Origen");
        SPANISH_FIELD_LABELS.put("targetcurrency", "Moneda Destino");
    }

    // Fallback dictionary of common Enum and Status values in Spanish
    private static final Map<String, String> SPANISH_ENUM_LABELS = new LinkedHashMap<>();
    static {
        SPANISH_ENUM_LABELS.put("ACTIVE", "Activo");
        SPANISH_ENUM_LABELS.put("INACTIVE", "Inactivo");
        SPANISH_ENUM_LABELS.put("SUSPENDED", "Suspendido");
        SPANISH_ENUM_LABELS.put("EXPIRED", "Vencido");
        SPANISH_ENUM_LABELS.put("TERMINATED", "Terminado");
        SPANISH_ENUM_LABELS.put("DRAFT", "Borrador");
        SPANISH_ENUM_LABELS.put("CANCELED", "Cancelado");
        SPANISH_ENUM_LABELS.put("CANCELLED", "Cancelado");
        SPANISH_ENUM_LABELS.put("PENDING", "Pendiente");
        SPANISH_ENUM_LABELS.put("APPROVED", "Aprobado");
        SPANISH_ENUM_LABELS.put("REJECTED", "Rechazado");
        SPANISH_ENUM_LABELS.put("VOIDED", "Anulado");
        SPANISH_ENUM_LABELS.put("DISPUTED", "En Disputa");
        SPANISH_ENUM_LABELS.put("PAID", "Pagado");
        SPANISH_ENUM_LABELS.put("PROPOSED", "Propuesto");
        SPANISH_ENUM_LABELS.put("OWNER", "Propietario");
        SPANISH_ENUM_LABELS.put("STAFF", "Personal");
        SPANISH_ENUM_LABELS.put("VIEWER", "Visor");
        SPANISH_ENUM_LABELS.put("CHILD", "Hijo/a");
        SPANISH_ENUM_LABELS.put("SPOUSE", "Cónyuge");
        SPANISH_ENUM_LABELS.put("PARENT", "Padre/Madre");
        SPANISH_ENUM_LABELS.put("SIBLING", "Hermano/a");
        SPANISH_ENUM_LABELS.put("OTHER", "Otro");
        SPANISH_ENUM_LABELS.put("INDIVIDUAL", "Individual");
        SPANISH_ENUM_LABELS.put("FAMILIAR", "Familiar");
        SPANISH_ENUM_LABELS.put("CORPORATIVO", "Corporativo");
        SPANISH_ENUM_LABELS.put("BANK_TRANSFER", "Transferencia Bancaria");
        SPANISH_ENUM_LABELS.put("CASH", "Efectivo");
        SPANISH_ENUM_LABELS.put("ZELLE", "Zelle");
        SPANISH_ENUM_LABELS.put("PAGO_MOVIL", "Pago Móvil");
        SPANISH_ENUM_LABELS.put("CRYPTO", "Cripto");
        SPANISH_ENUM_LABELS.put("INTERNATIONAL_TRANSFER", "Transferencia Internacional");
        SPANISH_ENUM_LABELS.put("COMMERCIAL", "Comercial");
        SPANISH_ENUM_LABELS.put("MEDICAL", "Médico");
        SPANISH_ENUM_LABELS.put("EXCLUSIVITY", "Exclusividad");
        SPANISH_ENUM_LABELS.put("SUPPLY", "Suministro");
        SPANISH_ENUM_LABELS.put("INSCRIPTION", "Inscripción");
        SPANISH_ENUM_LABELS.put("MONTHLY", "Mensualidad");
        SPANISH_ENUM_LABELS.put("PENDING_ENROLLMENT", "Por Inscribir");
        SPANISH_ENUM_LABELS.put("REGISTERED", "Registrado");
        SPANISH_ENUM_LABELS.put("REWARD_GRANTED", "Recompensa Otorgada");
        SPANISH_ENUM_LABELS.put("ALLY", "Aliado");
        SPANISH_ENUM_LABELS.put("PERCENTAGE", "Porcentaje");
        SPANISH_ENUM_LABELS.put("FLAT_AMOUNT", "Monto Fijo");
        SPANISH_ENUM_LABELS.put("EVERY_CYCLE", "Cada Ciclo");
        SPANISH_ENUM_LABELS.put("ONE_OFF", "Pago Único");
        SPANISH_ENUM_LABELS.put("MONTHLY_RESET", "Reinicio Mensual");
        SPANISH_ENUM_LABELS.put("CALENDAR_MONTH", "Mes Calendario");
        SPANISH_ENUM_LABELS.put("CALENDAR_YEAR", "Año Calendario");
        SPANISH_ENUM_LABELS.put("ALL_TIME", "Histórico General");
        SPANISH_ENUM_LABELS.put("LOCKED", "Bloqueado");
        SPANISH_ENUM_LABELS.put("IN", "Cobro (Entrada)");
        SPANISH_ENUM_LABELS.put("OUT", "Pago (Salida)");
    }

    public GenericEntityExtractorService(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.FIELD, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY)
                .setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor.GETTER, com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY);
        this.messageSource = messageSource;
    }

    /**
     * Natural sort priority for report fields:
     * 1. Codes and identifiers (10-29)
     * 2. Names and titles (30-59)
     * 3. Contact, location, and demographic data (60-99)
     * 4. Financial data and business dates (100-149)
     * 5. General / other fields (500)
     * 6. Status, publication, and review (800-899)
     * 7. Active flag and audit fields (900-999)
     */
    public int getFieldPriority(String key) {
        if (key == null || key.isBlank()) return 500;
        String lower = key.toLowerCase().replace("_", "");

        // 1. Identifiers, codes, and document numbers first
        if (lower.equals("code") || lower.equals("isocode") || lower.equals("codigo") || lower.equals("plancode") || lower.equals("referralcode")) {
            return 10;
        }
        if (lower.equals("taxdocumenttype") || lower.equals("documenttype") || lower.equals("tipodocumento")) {
            return 12;
        }
        if (lower.equals("taxdocumentnumber") || lower.equals("documentnumber") || lower.equals("rif") || lower.equals("cedula") || lower.equals("referencenumber") || lower.equals("taxdocument") || lower.equals("document") || lower.equals("person.document") || lower.equals("personrif")) {
            return 14;
        }

        // 2. Names, titles, and main types
        if (lower.equals("name") || lower.equals("nombre") || lower.equals("title") || lower.equals("displayname") || lower.equals("username")) {
            return 30;
        }
        if (lower.equals("fullname") || lower.equals("nombrecompleto") || lower.equals("person.fullname") || lower.equals("personfullname")) {
            return 32;
        }
        if (lower.equals("firstname") || lower.equals("primernombre")) {
            return 34;
        }
        if (lower.equals("middlename") || lower.equals("segundonombre")) {
            return 36;
        }
        if (lower.equals("lastname") || lower.equals("primerapellido")) {
            return 38;
        }
        if (lower.equals("secondlastname") || lower.equals("segundoapellido")) {
            return 40;
        }
        if (lower.equals("allytype") || lower.equals("promotertype") || lower.equals("plantype") || lower.equals("agreementtype") || lower.equals("type") || lower.equals("tipo") || lower.equals("allytype.name") || lower.equals("promotertype.name") || lower.equals("promotertypename")) {
            return 45;
        }
        if (lower.equals("servicecategory") || lower.equals("medicalspecialty") || lower.equals("specialty") || lower.equals("specialties") || lower.equals("services")) {
            return 48;
        }
        if (lower.equals("relationship") || lower.equals("parentesco") || lower.equals("allyrole") || lower.equals("role") || lower.equals("roles") || lower.equals("user") || lower.equals("person")) {
            return 50;
        }

        // 3. Contact, location, and personal details
        if (lower.equals("email") || lower.equals("correo") || lower.equals("correoelectronico") || lower.equals("useremail") || lower.equals("person.email") || lower.equals("user.email")) {
            return 60;
        }
        if (lower.equals("phone") || lower.equals("telefono") || lower.equals("celular") || lower.equals("landlinephone") || lower.equals("person.phone")) {
            return 62;
        }
        if (lower.equals("website") || lower.equals("sitioweb")) {
            return 66;
        }
        if (lower.equals("country") || lower.equals("pais") || lower.equals("country.name")) {
            return 70;
        }
        if (lower.equals("state") || lower.equals("estado_region") || lower.equals("region") || lower.equals("state.name")) {
            return 72;
        }
        if (lower.equals("city") || lower.equals("ciudad") || lower.equals("city.name")) {
            return 74;
        }
        if (lower.equals("address") || lower.equals("direccion") || lower.equals("employeraddress")) {
            return 76;
        }
        if (lower.equals("gender") || lower.equals("genero") || lower.equals("maritalstatus") || lower.equals("estadocivil")) {
            return 80;
        }
        if (lower.equals("birthdate") || lower.equals("fechanacimiento") || lower.equals("birthplace") || lower.equals("occupation")) {
            return 84;
        }
        if (lower.equals("employername") || lower.equals("jobposition") || lower.equals("spousename") || lower.equals("numberofchildren")) {
            return 88;
        }

        // 4. Financial data, fees, and operational dates
        if (lower.equals("direction") || lower.equals("paymentdirection") || lower.equals("flowdirection")) {
            return 98;
        }
        if (lower.equals("amount") || lower.equals("monto") || lower.equals("montocuota") || lower.equals("priceusd") || lower.equals("precio")) {
            return 100;
        }
        if (lower.equals("currency") || lower.equals("moneda") || lower.equals("paymentmethod") || lower.equals("metodopago")) {
            return 102;
        }
        if (lower.equals("discountpct") || lower.equals("descuento") || lower.equals("commissionpct") || lower.equals("flatamount")) {
            return 104;
        }
        if (lower.equals("inscriptionfee") || lower.equals("monthlyfee") || lower.equals("extrabeneficiaryinscriptionfee") || lower.equals("extrainscriptionpaid")) {
            return 106;
        }
        if (lower.equals("requiresappointment") || lower.equals("terms") || lower.equals("primary") || lower.equals("system") || lower.equals("issystem")) {
            return 110;
        }
        if (lower.equals("enrolledat") || lower.equals("joinedat") || lower.equals("paymentdate") || lower.equals("startdate") || lower.equals("fechaafiliacion")) {
            return 120;
        }
        if (lower.equals("enddate") || lower.equals("expiresat") || lower.equals("nextduedate") || lower.equals("lastpaidthrough") || lower.equals("graceperioddays")) {
            return 122;
        }
        if (lower.equals("totalreferrals") || lower.equals("receivedat")) {
            return 124;
        }
        if (lower.equals("totalcommissionpaid")) {
            return 200;
        }
        if (lower.equals("description") || lower.equals("descripcion") || lower.equals("notes") || lower.equals("notas") || lower.equals("adminnotes")) {
            return 140;
        }

        // 5. Status, publication, and review (towards end)
        if (lower.equals("reviewstatus") || lower.equals("reviewreason") || lower.equals("voidreason")) {
            return 800;
        }
        if (lower.equals("published") || lower.equals("ispublished") || lower.equals("publishedat")) {
            return 810;
        }
        if (lower.equals("status") || lower.equals("estado")) {
            return 850;
        }

        // 6. Active flag and audit fields (at the very end)
        if (lower.equals("active") || lower.equals("isactive") || lower.equals("esactivo") || lower.equals("activo")) {
            return 900;
        }
        if (lower.equals("confirmedat") || lower.equals("reviewedat") || lower.equals("earnedat") || lower.equals("paidat")) {
            return 950;
        }
        if (lower.equals("createdat") || lower.equals("fecharegistro") || lower.equals("fechacreacion")) {
            return 980;
        }
        if (lower.equals("updatedat") || lower.equals("fechaactualizacion")) {
            return 990;
        }

        return 500;
    }

    /**
     * Extracts metadata and key-value pairs from any DTO or Entity sorted by business priority.
     */
    public GenericRecordModel extractModel(Object targetObject, String title, String subtitle, String identifier, String generatedBy) {
        Map<String, String> fieldsMap = new LinkedHashMap<>();
        List<GenericRecordModel.DetailSection> detailSections = new ArrayList<>();
        Locale currentLocale = LocaleContextHolder.getLocale();

        if (targetObject != null) {
            Map<String, Object> map = toMap(targetObject);

            // Sort keys by business logic priority
            List<String> sortedKeys = new ArrayList<>(map.keySet());
            sortedKeys.sort(Comparator.comparingInt(this::getFieldPriority));

            for (String key : sortedKeys) {
                // If a _Display sibling exists for this field, skip the raw scalar property
                // so we don't display both raw and formatted values (e.g. amount vs amount_Display)
                if (map.containsKey(key + "_Display") || map.containsKey(key + "_display")) {
                    continue;
                }
                Object value = map.get(key);
                if (value != null && !isIgnoredField(key)) {
                    if (value instanceof Collection<?> collection) {
                        // If it's a collection of complex objects, process as detail table
                        GenericRecordModel.DetailSection section = extractDetailSection(targetObject, key, collection, currentLocale);
                        if (section != null) {
                            detailSections.add(section);
                        } else {
                            // If elements are simple/catalog items, format as comma-separated list
                            String formatted = formatValue(key, value);
                            if (!formatted.isBlank()) {
                                fieldsMap.put(resolveFieldLabel(key, currentLocale), formatted);
                            }
                        }
                    } else {
                        String label = resolveFieldLabel(key, currentLocale);
                        String formattedValue = formatValue(key, value);
                        fieldsMap.put(label, formattedValue);
                    }
                }
            }
        }

        String nowFormatted = LocalDateTime.now().format(dateTimeFormatter);
        String finalTitle = title != null && !title.isBlank() ? title : "Record Card";
        String finalUser = generatedBy != null && !generatedBy.isBlank() ? generatedBy : "System";

        return new GenericRecordModel(finalTitle, subtitle, identifier, nowFormatted, finalUser, fieldsMap, detailSections);
    }

    private boolean isIgnoredField(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) return true;
        String lower = fieldName.toLowerCase().replace("_", "");
        if (IGNORED_EXACT_FIELDS.contains(lower)) return true;
        if (lower.equals("uuid") || lower.endsWith("uuid")) return true;
        if (lower.endsWith("id") && !lower.equals("rif") && !lower.equals("paid") && !lower.equals("valid")) {
            return true;
        }
        return lower.contains("password") || lower.contains("secret") || lower.contains("token") || lower.contains("hash");
    }

    private boolean isParentReference(Object parentObject, String key, Object val) {
        if (key == null || key.isBlank()) return false;
        String lowerKey = key.toLowerCase().replace("_", "");

        // 1. Exclude universal parent relation keys
        if (Set.of("parent", "root", "parentrecord", "ownerrecord").contains(lowerKey)) {
            return true;
        }

        // 2. If value is same instance or matches parent type
        if (parentObject != null && val != null) {
            if (val == parentObject || val.getClass().equals(parentObject.getClass())) {
                return true;
            }
        }

        if (parentObject != null) {
            String parentClassName = parentObject.getClass().getSimpleName().toLowerCase();
            String cleanParentName = parentClassName
                    .replace("dto", "")
                    .replace("entity", "")
                    .replace("item", "");

            if (!cleanParentName.isBlank()) {
                // Matches parent resource name (e.g. "ally", "member", "plan", "promoter")
                if (lowerKey.equals(cleanParentName)) {
                    return true;
                }
                // Matches foreign key or attribute duplicating parent (e.g. "allyid", "allyuuid", "allyname", "membername")
                if (lowerKey.equals(cleanParentName + "id")
                        || lowerKey.equals(cleanParentName + "uuid")
                        || lowerKey.equals(cleanParentName + "name")
                        || lowerKey.equals(cleanParentName + "fullname")) {
                    return true;
                }
            }
        }

        // 3. Complex JPA entities pointing to common parent entities
        if (Set.of("ally", "member", "membership", "promoter", "plan", "corporatecontract").contains(lowerKey)) {
            return val != null && !(val instanceof String || val instanceof Number || val instanceof Boolean || val instanceof Enum<?>);
        }

        return false;
    }

    public String resolveFieldLabel(String key, Locale locale) {
        if (key == null || key.isBlank()) return "";

        String cleanKey = key.trim();
        if (cleanKey.endsWith("_Display") || cleanKey.endsWith("_display")) {
            cleanKey = cleanKey.substring(0, cleanKey.length() - 8);
        } else if (cleanKey.endsWith("Display") && cleanKey.length() > 7 && Character.isLowerCase(cleanKey.charAt(cleanKey.length() - 8))) {
            cleanKey = cleanKey.substring(0, cleanKey.length() - 7);
        }
        if (cleanKey.endsWith("_Uuid") || cleanKey.endsWith("_uuid")) {
            cleanKey = cleanKey.substring(0, cleanKey.length() - 5);
        }

        Locale targetLocale = (locale != null) ? locale : venezuelaLocale;

        // 1. Dynamic i18n lookup via Spring MessageSource
        if (messageSource != null) {
            String snake = toSnakeCase(cleanKey);
            for (String msgKey : List.of(
                    "report.field." + cleanKey,
                    "field." + cleanKey,
                    "report.field." + snake,
                    "field." + snake,
                    cleanKey,
                    snake
            )) {
                try {
                    String msg = messageSource.getMessage(msgKey, null, null, targetLocale);
                    if (msg != null && !msg.isBlank()) return msg;
                } catch (Exception ignored) {}
            }
        }

        // 2. Specific nested/catalog property label helpers
        if (cleanKey.equalsIgnoreCase("allyType.name")) return "Tipo de Aliado";
        if (cleanKey.equalsIgnoreCase("promoterType.name")) return "Tipo de Promotor";
        if (cleanKey.equalsIgnoreCase("city.name")) return "Ciudad";
        if (cleanKey.equalsIgnoreCase("state.name")) return "Estado / Región";
        if (cleanKey.equalsIgnoreCase("country.name")) return "País";
        if (cleanKey.endsWith(".fullName")) return "Nombre Completo";
        if (cleanKey.endsWith(".document")) return "Cédula / Documento";
        if (cleanKey.endsWith(".documentNumber")) return "Número de Cédula";
        if (cleanKey.endsWith(".phone")) return "Teléfono";
        if (cleanKey.endsWith(".email")) return "Correo Electrónico";
        if (cleanKey.endsWith(".name")) {
            String prefix = cleanKey.substring(0, cleanKey.indexOf("."));
            String lowerPrefix = prefix.toLowerCase().replace("_", "");
            if (SPANISH_FIELD_LABELS.containsKey(lowerPrefix)) {
                return SPANISH_FIELD_LABELS.get(lowerPrefix);
            }
            return formatCamelCaseToTitle(prefix);
        }
        if (cleanKey.equals("taxDocument")) return "RIF / Documento";
        if (cleanKey.equals("document")) return "Cédula / Documento";

        // 3. Fallback lookup in normalized dictionary
        String lookupKey = cleanKey.trim();
        if (SPANISH_FIELD_LABELS.containsKey(lookupKey)) {
            return SPANISH_FIELD_LABELS.get(lookupKey);
        }
        String lowerKey = lookupKey.toLowerCase().replace("_", "");
        if (SPANISH_FIELD_LABELS.containsKey(lowerKey)) {
            return SPANISH_FIELD_LABELS.get(lowerKey);
        }

        return formatCamelCaseToTitle(cleanKey);
    }

    private String toSnakeCase(String str) {
        if (str == null) return "";
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private String formatCamelCaseToTitle(String camelCase) {
        if (camelCase == null || camelCase.isBlank()) return "";
        StringBuilder result = new StringBuilder();
        char[] chars = camelCase.trim().toCharArray();
        result.append(Character.toUpperCase(chars[0]));

        for (int i = 1; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                result.append(' ');
                result.append(c);
            } else if (c == '_' || c == '-') {
                result.append(' ');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public String formatValue(String key, Object val) {
        if (val == null) return "";

        if (val instanceof BigDecimal bd) {
            NumberFormat nf = NumberFormat.getNumberInstance(venezuelaLocale);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return nf.format(bd);
        }

        if (val instanceof Double d) {
            NumberFormat nf = NumberFormat.getNumberInstance(venezuelaLocale);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return nf.format(d);
        }

        if (val instanceof Float f) {
            NumberFormat nf = NumberFormat.getNumberInstance(venezuelaLocale);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return nf.format(f);
        }

        if (val instanceof Number n) {
            return n.toString();
        }

        if (val instanceof Boolean b) {
            Locale currentLocale = LocaleContextHolder.getLocale();
            Locale targetLocale = (currentLocale != null) ? currentLocale : venezuelaLocale;
            if (messageSource != null) {
                try {
                    String msg = messageSource.getMessage(b ? "common.yes" : "common.no", null, null, targetLocale);
                    if (msg != null && !msg.isBlank()) return msg;
                } catch (Exception ignored) {}
            }
            return b ? "Sí" : "No";
        }

        if (val instanceof LocalDate ld) {
            return ld.format(dateFormatter);
        }

        if (val instanceof LocalDateTime ldt) {
            return ldt.format(dateTimeFormatter);
        }

        if (val instanceof OffsetDateTime odt) {
            return odt.format(dateTimeFormatter);
        }

        if (val instanceof Instant inst) {
            return LocalDateTime.ofInstant(inst, ZoneId.systemDefault()).format(dateTimeFormatter);
        }

        if (val instanceof LocalTime lt) {
            return lt.format(timeFormatter);
        }

        if (val instanceof Enum<?> e) {
            return resolveEnumLabel(e);
        }

        if (val instanceof String s) {
            return resolveStringValue(key, s);
        }

        if (val instanceof Collection<?> collection) {
            List<String> items = new ArrayList<>();
            for (Object item : collection) {
                if (item != null) {
                    String str = formatValue(key, item);
                    if (!str.isBlank()) {
                        items.add(str);
                    }
                }
            }
            return String.join(", ", items);
        }

        if (val instanceof Map<?, ?> map) {
            for (String k : List.of("fullName", "displayName", "name", "title", "label", "description", "email", "code")) {
                if (map.containsKey(k) && map.get(k) != null && !String.valueOf(map.get(k)).isBlank()) {
                    return String.valueOf(map.get(k));
                }
            }
            if (map.containsKey("firstName") || map.containsKey("lastName")) {
                String fn = map.get("firstName") != null ? String.valueOf(map.get("firstName")) : "";
                String ln = map.get("lastName") != null ? String.valueOf(map.get("lastName")) : "";
                String full = (fn + " " + ln).trim();
                if (!full.isBlank()) return full;
            }
            if (map.containsKey("person") && map.get("person") != null) {
                String personDisplay = formatValue("person", map.get("person"));
                if (!personDisplay.isBlank()) return personDisplay;
            }
            return "";
        }

        // Entity / DTO inspection to extract representative name or label
        String extractedDisplay = extractDisplayStringFromObject(val);
        if (extractedDisplay != null) {
            return extractedDisplay;
        }

        // If custom toString() exists use it; avoid ClassName@hash
        try {
            Method toStringMethod = val.getClass().getMethod("toString");
            if (toStringMethod.getDeclaringClass() != Object.class) {
                return val.toString();
            }
        } catch (Exception ignored) {}

        return "";
    }

    private String resolveStringValue(String fieldKey, String str) {
        if (str == null || str.isBlank()) return "";
        String trimmed = str.trim();
        String upper = trimmed.toUpperCase();

        Locale currentLocale = LocaleContextHolder.getLocale();
        Locale targetLocale = (currentLocale != null) ? currentLocale : venezuelaLocale;

        // 1. Lookup dynamic keys via MessageSource
        if (messageSource != null) {
            List<String> candidateKeys = new ArrayList<>();
            if (fieldKey != null && !fieldKey.isBlank()) {
                String cleanField = fieldKey.toLowerCase().replace("_", "");
                candidateKeys.add("status." + trimmed);
                candidateKeys.add("status." + upper);
                candidateKeys.add(cleanField + "." + trimmed);
                candidateKeys.add(cleanField + "." + upper);
                candidateKeys.add("allies.agreements.status." + upper);
                candidateKeys.add("allies.services.reviewStatus." + upper);
                candidateKeys.add("allies.staff.roles." + upper);
                candidateKeys.add("allies.agreements.types." + upper);
                candidateKeys.add("allies.status." + upper);
                candidateKeys.add("plans.types." + upper);
                candidateKeys.add("payments.methods." + upper);
                candidateKeys.add("payments.status." + upper);
                candidateKeys.add("memberships.status." + upper);
                candidateKeys.add("members.relationships." + upper);
                candidateKeys.add("members.status." + upper);
            }
            candidateKeys.add("enum." + upper);
            candidateKeys.add(trimmed);
            candidateKeys.add(upper);

            for (String key : candidateKeys) {
                try {
                    String msg = messageSource.getMessage(key, null, null, targetLocale);
                    if (msg != null && !msg.isBlank() && !msg.equals(key)) {
                        return msg;
                    }
                } catch (Exception ignored) {}
            }
        }

        // 2. Fallback dictionary for Enums and Statuses in Spanish
        if (SPANISH_ENUM_LABELS.containsKey(upper)) {
            return SPANISH_ENUM_LABELS.get(upper);
        }

        return trimmed;
    }

    private String extractDisplayStringFromObject(Object val) {
        if (val == null) return null;

        if (val instanceof DisplayRef ref) {
            if (ref.name() != null && !ref.name().isBlank()) {
                if (ref.code() != null && !ref.code().isBlank() && !ref.name().equalsIgnoreCase(ref.code())) {
                    return ref.name() + " (" + ref.code() + ")";
                }
                return ref.name();
            }
            if (ref.code() != null && !ref.code().isBlank()) {
                return ref.code();
            }
            return "";
        }

        Class<?> clazz = val.getClass();

        // 1. Standard name or label getter methods
        for (String getterName : List.of("getFullName", "getDisplayName", "getName", "getTitle", "getLabel", "getDescription", "getCode", "getEmail")) {
            try {
                Method method = clazz.getMethod(getterName);
                if (method.getParameterCount() == 0 && String.class.isAssignableFrom(method.getReturnType())) {
                    Object result = method.invoke(val);
                    if (result != null && !result.toString().isBlank()) {
                        return result.toString();
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. If User entity with getPerson()
        try {
            Method getPerson = clazz.getMethod("getPerson");
            if (getPerson.getParameterCount() == 0) {
                Object person = getPerson.invoke(val);
                if (person != null) {
                    String personDisplay = extractDisplayStringFromObject(person);
                    if (personDisplay != null && !personDisplay.isBlank()) {
                        return personDisplay;
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. firstName + lastName combination if present
        try {
            Method getFirstName = clazz.getMethod("getFirstName");
            Method getLastName = clazz.getMethod("getLastName");
            if (getFirstName.getParameterCount() == 0 && getLastName.getParameterCount() == 0) {
                Object fn = getFirstName.invoke(val);
                Object ln = getLastName.invoke(val);
                String first = fn != null ? fn.toString() : "";
                String last = ln != null ? ln.toString() : "";
                String full = (first + " " + last).trim();
                if (!full.isBlank()) {
                    return full;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private String resolveEnumLabel(Enum<?> e) {
        if (e == null) return "";
        Locale currentLocale = LocaleContextHolder.getLocale();
        Locale targetLocale = (currentLocale != null) ? currentLocale : venezuelaLocale;

        if (messageSource != null) {
            String enumClassName = e.getDeclaringClass().getSimpleName();
            for (String key : List.of(
                    "plans.types." + e.name(),
                    "enum." + enumClassName + "." + e.name(),
                    "enum." + e.name(),
                    e.name()
            )) {
                try {
                    String msg = messageSource.getMessage(key, null, null, targetLocale);
                    if (msg != null && !msg.isBlank()) return msg;
                } catch (Exception ignored) {}
            }
        }

        String enumKey = e.name().toUpperCase();
        if (SPANISH_ENUM_LABELS.containsKey(enumKey)) {
            return SPANISH_ENUM_LABELS.get(enumKey);
        }

        return formatCamelCaseToTitle(e.name());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return Collections.emptyMap();
        if (obj instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            Map<String, Object> result = objectMapper.readValue(json, MAP_TYPE_REF);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        } catch (Exception ignored) {
        }

        // Reflection fallback for JPA entities or objects with getters
        Map<String, Object> map = new LinkedHashMap<>();
        for (Method method : obj.getClass().getMethods()) {
            if (method.getParameterCount() == 0 && (method.getName().startsWith("get") || method.getName().startsWith("is"))) {
                if (method.getName().equals("getClass") || method.getName().equals("getHibernateLazyInitializer")) {
                    continue;
                }
                String name = method.getName().startsWith("get") ? method.getName().substring(3) : method.getName().substring(2);
                if (!name.isEmpty()) {
                    String key = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                    try {
                        Object val = method.invoke(obj);
                        if (val != null) {
                            map.put(key, val);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return map;
    }

    private GenericRecordModel.DetailSection extractDetailSection(Object parentObject, String sectionName, Collection<?> collection, Locale locale) {
        if (collection == null || collection.isEmpty()) return null;

        // Check if elements have table structure (multiple properties)
        Object first = collection.iterator().next();
        if (first == null) return null;
        Map<String, Object> firstMap = toMap(first);
        if (firstMap.size() <= 1 && (firstMap.containsKey("name") || firstMap.containsKey("code") || first instanceof String)) {
            // Simple list of names/labels, best rendered inline
            return null;
        }

        List<String> headers = new ArrayList<>();
        List<String> fieldKeys = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        for (Object item : collection) {
            if (item == null) continue;
            Map<String, Object> itemMap = toMap(item);

            if (headers.isEmpty()) {
                List<String> candidateKeys = new ArrayList<>(itemMap.keySet());
                candidateKeys.sort(Comparator.comparingInt(this::getFieldPriority));

                for (String k : candidateKeys) {
                    Object v = itemMap.get(k);
                    if (!isIgnoredField(k) && !isParentReference(parentObject, k, v) && !(v instanceof Collection<?>) && fieldKeys.size() < 8) {
                        fieldKeys.add(k);
                        headers.add(resolveFieldLabel(k, locale));
                    }
                }
            }

            List<String> row = new ArrayList<>();
            for (String k : fieldKeys) {
                Object val = itemMap.get(k);
                row.add(formatValue(k, val));
            }
            rows.add(row);
        }

        if (headers.isEmpty()) return null;
        String sectionTitle = resolveFieldLabel(sectionName, locale);
        return new GenericRecordModel.DetailSection(sectionTitle, headers, rows);
    }

    private boolean isPrimitiveOrCommonValue(Object val) {
        if (val == null) return true;
        return val instanceof String
                || val instanceof Number
                || val instanceof Boolean
                || val instanceof LocalDate
                || val instanceof LocalDateTime
                || val instanceof LocalTime
                || val instanceof OffsetDateTime
                || val instanceof Instant
                || val instanceof Enum<?>
                || val instanceof UUID;
    }

    private void flattenNestedMap(String prefix, Map<?, ?> nestedMap, Map<String, Object> rootRaw, Map<String, Object> result) {
        boolean hasRootName = rootRaw.containsKey("displayName") || rootRaw.containsKey("name") || rootRaw.containsKey("fullName");
        boolean hasRootEmail = rootRaw.containsKey("email");
        boolean hasRootPhone = rootRaw.containsKey("phone");
        boolean hasRootDoc = rootRaw.containsKey("document") || rootRaw.containsKey("taxDocument") || (rootRaw.containsKey("documentNumber") && rootRaw.containsKey("documentType"));

        if (!hasRootName) {
            if (nestedMap.containsKey("fullName") && nestedMap.get("fullName") != null) {
                result.put(prefix + ".fullName", nestedMap.get("fullName"));
            } else if (nestedMap.containsKey("firstName") || nestedMap.containsKey("lastName")) {
                String fn = nestedMap.get("firstName") != null ? String.valueOf(nestedMap.get("firstName")) : "";
                String ln = nestedMap.get("lastName") != null ? String.valueOf(nestedMap.get("lastName")) : "";
                String full = (fn + " " + ln).trim();
                if (!full.isBlank()) {
                    result.put(prefix + ".fullName", full);
                }
            }
        }

        if (!hasRootDoc) {
            if (nestedMap.containsKey("documentType") && nestedMap.containsKey("documentNumber")) {
                Object dt = nestedMap.get("documentType");
                Object dn = nestedMap.get("documentNumber");
                if (dn != null && !dn.toString().isBlank()) {
                    result.put(prefix + ".document", (dt != null ? dt + "-" : "") + dn);
                }
            } else if (nestedMap.containsKey("documentNumber") && nestedMap.get("documentNumber") != null) {
                result.put(prefix + ".documentNumber", nestedMap.get("documentNumber"));
            } else if (nestedMap.containsKey("rif") && nestedMap.get("rif") != null) {
                result.put(prefix + ".document", nestedMap.get("rif"));
            }
        }

        if (nestedMap.containsKey("name") && nestedMap.get("name") != null && !hasRootName) {
            result.put(prefix + ".name", nestedMap.get("name"));
        } else if (nestedMap.containsKey("name") && nestedMap.get("name") != null && !prefix.equals("user") && !prefix.equals("person")) {
            result.put(prefix + ".name", nestedMap.get("name"));
        } else if (nestedMap.containsKey("code") && nestedMap.get("code") != null && !rootRaw.containsKey("code")) {
            result.put(prefix + ".code", nestedMap.get("code"));
        }

        if (!hasRootPhone && nestedMap.containsKey("phone") && nestedMap.get("phone") != null) {
            result.put(prefix + ".phone", nestedMap.get("phone"));
        }
        if (!hasRootEmail && nestedMap.containsKey("email") && nestedMap.get("email") != null) {
            result.put(prefix + ".email", nestedMap.get("email"));
        }
    }

    /**
     * Name-based, not type-based — a bare key like {@code "rank"} is excluded
     * for every entity, not just {@link
     * com.fenixcore.optibienestar360.modules.promoter.entity.Promoter#getRank()}
     * (V101). Currently safe: {@code LeaderboardPrize}/{@code
     * LeaderboardPrizeAward} also have an unrelated primitive {@code int rank}
     * (leaderboard position), but nothing in {@code main} wires either of
     * those entities through {@link #extractTableModel}/{@link
     * #extractModel} today — if that changes, this key collision will need a
     * type-aware exclusion instead of a name-based one.
     */
    private boolean isComplexRelation(String key) {
        if (key == null || key.isBlank()) return false;
        String lower = key.toLowerCase().replace("_", "");
        return Set.of(
                "user", "person", "promotertype", "allytype", "city", "state", "country",
                "member", "membership", "plan", "ally", "corporatecontract", "servicecategory",
                "rule", "promoter", "agreement", "rank", "supervisor"
        ).contains(lower);
    }

    private Map<String, Object> flattenEntity(Object obj) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (obj == null) return result;

        Map<String, Object> raw = toMap(obj);

        raw.forEach((k, v) -> {
            if (isIgnoredField(k) || v instanceof Collection<?>) return;

            if (v instanceof Map<?, ?> nestedMap) {
                flattenNestedMap(k, nestedMap, raw, result);
            } else if (v != null && !isPrimitiveOrCommonValue(v)) {
                Map<String, Object> nestedMap = toMap(v);
                if (!nestedMap.isEmpty()) {
                    flattenNestedMap(k, nestedMap, raw, result);
                }
            } else if (!isComplexRelation(k)) {
                result.put(k, v);
            }
        });

        // Helper para documentos tributarios (RIF) y cédulas en la raíz del mapa
        if (raw.containsKey("taxDocumentType") && raw.containsKey("taxDocumentNumber")) {
            Object tt = raw.get("taxDocumentType");
            Object tn = raw.get("taxDocumentNumber");
            if (tn != null && !tn.toString().isBlank()) {
                result.put("taxDocument", (tt != null ? tt.toString() + "-" : "") + tn.toString());
            }
        }
        if (raw.containsKey("documentType") && raw.containsKey("documentNumber")) {
            Object dt = raw.get("documentType");
            Object dn = raw.get("documentNumber");
            if (dn != null && !dn.toString().isBlank()) {
                result.put("document", (dt != null ? dt.toString() + "-" : "") + dn.toString());
            }
        }

        return result;
    }

    /**
     * Extrae un modelo de tabla plana (lista de filas y columnas) a partir de una colección de registros.
     * Selecciona prioritariamente las columnas de negocio clave visibles en el sistema ordenadas por prioridad de negocio.
     */
    @SuppressWarnings("unchecked")
    public GenericTableModel extractTableModel(
            Collection<?> collection, String title, String subtitle, String generatedBy) {
        Locale currentLocale = LocaleContextHolder.getLocale();
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        if (collection != null && !collection.isEmpty()) {
            List<Map<String, Object>> flattenedItems = new ArrayList<>(collection.size());
            Set<String> allKeys = new LinkedHashSet<>();

            for (Object item : collection) {
                if (item == null) continue;
                Map<String, Object> itemMap = flattenEntity(item);
                flattenedItems.add(itemMap);
                allKeys.addAll(itemMap.keySet());
            }

            // Filtrar campos ignorados y ordenar todas las claves candidatas según la prioridad de negocio
            List<String> candidateKeys = allKeys.stream()
                    .filter(k -> !isIgnoredField(k))
                    .sorted(Comparator.comparingInt(this::getFieldPriority))
                    .toList();

            // Seleccionar hasta un máximo de 10 columnas en orden de relevancia
            List<String> selectedKeys = new ArrayList<>();
            for (String key : candidateKeys) {
                if (selectedKeys.size() >= 10) break;
                String lowerKey = key.toLowerCase().replace("_", "");
                // Omitir campos de texto largo si hay suficientes columnas de datos estructurados
                if ((lowerKey.equals("description") || lowerKey.equals("descripcion") || lowerKey.equals("notes") || lowerKey.equals("notas") || lowerKey.equals("adminnotes") || lowerKey.equals("terms"))
                        && candidateKeys.size() > 10) {
                    continue;
                }
                selectedKeys.add(key);
                headers.add(resolveFieldLabel(key, currentLocale));
            }

            for (Map<String, Object> itemMap : flattenedItems) {
                List<String> row = new ArrayList<>(selectedKeys.size());
                for (String key : selectedKeys) {
                    Object val = itemMap.get(key);
                    row.add(formatValue(key, val));
                }
                rows.add(row);
            }
        }

        String nowFormatted = LocalDateTime.now().format(dateTimeFormatter);
        String finalTitle = (title != null && !title.isBlank()) ? title : "Listado de Registros";
        String finalUser = (generatedBy != null && !generatedBy.isBlank()) ? generatedBy : "Sistema OptiBienestar 360";

        return new GenericTableModel(
                finalTitle, subtitle, nowFormatted, finalUser, headers, rows, rows.size());
    }
}
