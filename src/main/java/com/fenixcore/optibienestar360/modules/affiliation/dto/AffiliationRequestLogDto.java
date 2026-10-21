package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequestLog.Action;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * One row of a request's history. Status changes carry
 * {@code fromStatus → toStatus}; edits carry {@code changes}, each
 * {@code {field, from, to}} with {@code field} the request field name
 * ({@code firstName}, {@code documentNumber}, {@code plan}…). {@code actor}
 * is absent on the CREATED row (the applicant is anonymous).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AffiliationRequestLogDto(
        @Display(value = Display.Kind.ENUM, enumScope = "affiliation_request.action") Action action,
        @Display(value = Display.Kind.ENUM, enumScope = "affiliation_request.status") String fromStatus,
        @Display(value = Display.Kind.ENUM, enumScope = "affiliation_request.status") String toStatus,
        List<Map<String, String>> changes,
        String comment,
        @Display DisplayRef actor,
        @Display(Display.Kind.DATETIME) Instant actionAt
) {}
