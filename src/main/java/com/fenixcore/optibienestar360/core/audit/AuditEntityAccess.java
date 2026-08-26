package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.audit.repository.ReportAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Dynamic {@code @PreAuthorize} check for {@code AdminDataChangeAuditController}/
 * {@code AdminReportAuditController}: grants access either via the blanket
 * {@code AUDIT_VIEW_ALL}/{@code REPORT_AUDIT_VIEW_ALL} (all entities) or the granular
 * {@code <DOMAIN>_RECORD_AUDIT_VIEW}/{@code <DOMAIN>_REPORT_AUDIT_VIEW} permission for the
 * specific {@code entityKey} being queried (V66/V72/V77). The granular data-change permissions
 * were introduced in V66 as {@code <DOMAIN>_AUDIT_VIEW} but never consumed by any endpoint until
 * now — see spec 16-audit.md; renamed to {@code <DOMAIN>_RECORD_AUDIT_VIEW} in V77 to remove the
 * suffix collision with {@code <DOMAIN>_REPORT_AUDIT_VIEW} (both used to end in "_AUDIT_VIEW").
 *
 * <p>{@code entityKey} values come from {@link Auditable#entity()} across the codebase and
 * are finer-grained than the 10 permission domains (e.g. {@code ally}, {@code ally_type},
 * {@code ally_agreement} all fold into the {@code ALLIES} domain), so this holds an
 * explicit many-to-one map rather than deriving the permission from the key.</p>
 */
@Component("auditAccess")
@RequiredArgsConstructor
public class AuditEntityAccess {

	private final ReportAuditLogRepository reportAuditLogRepository;

	private static final String VIEW_ALL = "AUDIT_VIEW_ALL";

	private static final Map<String, String> ENTITY_TO_PERMISSION = Map.ofEntries(
		Map.entry("user", "USER_RECORD_AUDIT_VIEW"),
		Map.entry("role", "ROLE_RECORD_AUDIT_VIEW"),
		Map.entry("user_role", "ROLE_RECORD_AUDIT_VIEW"),
		Map.entry("member", "MEMBER_RECORD_AUDIT_VIEW"),
		Map.entry("member_document", "MEMBER_RECORD_AUDIT_VIEW"),
		Map.entry("member_promoter", "MEMBER_RECORD_AUDIT_VIEW"),
		Map.entry("beneficiary", "MEMBER_RECORD_AUDIT_VIEW"),
		Map.entry("medical_record", "MEMBER_RECORD_AUDIT_VIEW"),
		Map.entry("benefit_usage", "MEMBER_RECORD_AUDIT_VIEW"),
		Map.entry("ally", "ALLY_RECORD_AUDIT_VIEW"),
		Map.entry("ally_type", "ALLY_RECORD_AUDIT_VIEW"),
		Map.entry("ally_agreement", "ALLY_RECORD_AUDIT_VIEW"),
		Map.entry("ally_service", "ALLY_RECORD_AUDIT_VIEW"),
		Map.entry("ally_user", "ALLY_RECORD_AUDIT_VIEW"),
		Map.entry("plan", "PLAN_RECORD_AUDIT_VIEW"),
		Map.entry("membership", "MEMBERSHIP_RECORD_AUDIT_VIEW"),
		Map.entry("corporate_contract", "MEMBERSHIP_RECORD_AUDIT_VIEW"),
		Map.entry("subsidy", "MEMBERSHIP_RECORD_AUDIT_VIEW"),
		Map.entry("payment", "PAYMENT_RECORD_AUDIT_VIEW"),
		Map.entry("promoter", "PROMOTER_RECORD_AUDIT_VIEW"),
		Map.entry("commission_tier", "COMMISSION_RECORD_AUDIT_VIEW"),
		Map.entry("bonus_rule", "COMMISSION_RECORD_AUDIT_VIEW"),
		Map.entry("referral", "REFERRAL_RECORD_AUDIT_VIEW"),
		Map.entry("country", "COUNTRY_RECORD_AUDIT_VIEW"),
		Map.entry("state", "STATE_RECORD_AUDIT_VIEW"),
		Map.entry("city", "CITY_RECORD_AUDIT_VIEW"),
		Map.entry("gender", "GENDER_RECORD_AUDIT_VIEW"),
		Map.entry("document_type", "DOCUMENT_TYPE_RECORD_AUDIT_VIEW"),
		Map.entry("marital_status", "MARITAL_STATUS_RECORD_AUDIT_VIEW"),
		Map.entry("occupation", "OCCUPATION_RECORD_AUDIT_VIEW"),
		Map.entry("medical_specialty", "MEDICAL_SPECIALTY_RECORD_AUDIT_VIEW"),
		Map.entry("service_category", "SERVICE_CATEGORY_RECORD_AUDIT_VIEW"),
		Map.entry("promoter_type", "PROMOTER_TYPE_RECORD_AUDIT_VIEW"))
	;

	private static final String REPORT_VIEW_ALL = "REPORT_AUDIT_VIEW_ALL";

	// GenericDocumentController derives entityKey at runtime from the requested table/entity
	// path segment (lower_snake_case), so it shares the same value space as @Auditable's
	// entityKey — hence the same map, pointed at the <DOMAIN>_REPORT_AUDIT_VIEW permissions
	// added in V72 instead of <DOMAIN>_RECORD_AUDIT_VIEW.
	private static final Map<String, String> ENTITY_TO_REPORT_PERMISSION = Map.ofEntries(
		Map.entry("user", "USER_REPORT_AUDIT_VIEW"),
		Map.entry("role", "ROLE_REPORT_AUDIT_VIEW"),
		Map.entry("user_role", "ROLE_REPORT_AUDIT_VIEW"),
		Map.entry("member", "MEMBER_REPORT_AUDIT_VIEW"),
		Map.entry("member_document", "MEMBER_REPORT_AUDIT_VIEW"),
		Map.entry("member_promoter", "MEMBER_REPORT_AUDIT_VIEW"),
		Map.entry("beneficiary", "MEMBER_REPORT_AUDIT_VIEW"),
		Map.entry("medical_record", "MEMBER_REPORT_AUDIT_VIEW"),
		Map.entry("benefit_usage", "MEMBER_REPORT_AUDIT_VIEW"),
		Map.entry("ally", "ALLY_REPORT_AUDIT_VIEW"),
		Map.entry("ally_type", "ALLY_REPORT_AUDIT_VIEW"),
		Map.entry("ally_agreement", "ALLY_REPORT_AUDIT_VIEW"),
		Map.entry("ally_service", "ALLY_REPORT_AUDIT_VIEW"),
		Map.entry("ally_user", "ALLY_REPORT_AUDIT_VIEW"),
		Map.entry("plan", "PLAN_REPORT_AUDIT_VIEW"),
		Map.entry("membership", "MEMBERSHIP_REPORT_AUDIT_VIEW"),
		Map.entry("corporate_contract", "MEMBERSHIP_REPORT_AUDIT_VIEW"),
		Map.entry("subsidy", "MEMBERSHIP_REPORT_AUDIT_VIEW"),
		Map.entry("payment", "PAYMENT_REPORT_AUDIT_VIEW"),
		Map.entry("promoter", "PROMOTER_REPORT_AUDIT_VIEW"),
		Map.entry("commission_tier", "COMMISSION_REPORT_AUDIT_VIEW"),
		Map.entry("bonus_rule", "COMMISSION_REPORT_AUDIT_VIEW"),
		Map.entry("referral", "REFERRAL_REPORT_AUDIT_VIEW"),
		Map.entry("country", "COUNTRY_REPORT_AUDIT_VIEW"),
		Map.entry("state", "STATE_REPORT_AUDIT_VIEW"),
		Map.entry("city", "CITY_REPORT_AUDIT_VIEW"),
		Map.entry("gender", "GENDER_REPORT_AUDIT_VIEW"),
		Map.entry("document_type", "DOCUMENT_TYPE_REPORT_AUDIT_VIEW"),
		Map.entry("marital_status", "MARITAL_STATUS_REPORT_AUDIT_VIEW"),
		Map.entry("occupation", "OCCUPATION_REPORT_AUDIT_VIEW"),
		Map.entry("medical_specialty", "MEDICAL_SPECIALTY_REPORT_AUDIT_VIEW"),
		Map.entry("service_category", "SERVICE_CATEGORY_REPORT_AUDIT_VIEW"),
		Map.entry("promoter_type", "PROMOTER_TYPE_REPORT_AUDIT_VIEW"))
	;

	/**
	 * @param entityKey the {@code entityKey} query filter; {@code null}/blank means an
	 *                  unscoped, cross-entity query, which only {@code AUDIT_VIEW_ALL} may run
	 */
	public boolean canView(String entityKey) {
		return canView(entityKey, VIEW_ALL, ENTITY_TO_PERMISSION);
	}

	/**
	 * Same as {@link #canView(String)} but for the report-generation audit trail
	 * ({@code GET /v1/admin/audit/reports}), gated by {@code REPORT_AUDIT_VIEW_ALL} plus the
	 * V72 {@code <DOMAIN>_REPORT_AUDIT_VIEW} permissions.
	 */
	public boolean canViewReports(String entityKey) {
		return canView(entityKey, REPORT_VIEW_ALL, ENTITY_TO_REPORT_PERMISSION);
	}

	/**
	 * Same check as {@link #canViewReports(String)} but for {@code /reports/{uuid}/download},
	 * which only gets a UUID — the entity key is looked up from the log row itself. A missing
	 * row is not this bean's concern to report (404 is the controller/service's job): treat it
	 * as "no entity key", which only {@code REPORT_AUDIT_VIEW_ALL} can pass.
	 */
	public boolean canViewReportDownload(UUID reportUuid) {
		String entityKey = reportAuditLogRepository
			.findByUuid(reportUuid)
			.map(log -> log.getEntityKey())
			.orElse(null)
		;
		return canViewReports(entityKey);
	}

	private boolean canView(String entityKey, String viewAllAuthority, Map<String, String> permissionsByEntity) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null) {
			return false;
		}
		if (hasAuthority(auth, viewAllAuthority)) {
			return true;
		}
		if (entityKey == null || entityKey.isBlank()) {
			return false;
		}
		String domainPermission = permissionsByEntity.get(entityKey);
		return domainPermission != null && hasAuthority(auth, domainPermission);
	}

	private boolean hasAuthority(Authentication auth, String authority) {
		return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
	}

}
