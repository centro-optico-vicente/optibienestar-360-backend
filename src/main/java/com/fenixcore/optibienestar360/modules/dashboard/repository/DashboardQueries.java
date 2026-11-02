package com.fenixcore.optibienestar360.modules.dashboard.repository;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only aggregations behind the home dashboard ({@code GET /v1/me/dashboard}).
 *
 * <p>Native PostgreSQL on purpose: every figure is a single grouped query
 * ({@code COUNT} / {@code SUM} with {@code GROUP BY} month or status), so the
 * panel costs a fixed handful of round-trips whatever the data volume — no
 * entity loading, no N+1. Months are bucketed as {@code yyyy-MM} strings in the
 * app timezone ({@link AppTimeZone}); instants are shifted with
 * {@code AT TIME ZONE} before truncation.</p>
 *
 * <p>Scopes are optional filters appended as fixed SQL fragments (never user
 * input), so one query serves the program-wide view and the per-advisor /
 * per-ally / per-member views.</p>
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardQueries {

    /** Leading space on purpose: text blocks strip the trailing space of the {@code WHERE} line. */
    private static final String LIVE_USAGE = " bu.is_active AND bu.status = 'REGISTERED'";

    private final NamedParameterJdbcTemplate jdbc;

    // ─── Scope of benefit-usage aggregates ────────────────────────────────────

    /**
     * Restricts usage aggregates: all null = program-wide; {@code allyIds} = an
     * ally operator's establishments; {@code memberId} = a holder's whole
     * family; {@code beneficiaryId} = one beneficiary with access.
     */
    public record UsageScope(Collection<Long> allyIds, Long memberId, Long beneficiaryId) {
        public static final UsageScope ALL = new UsageScope(null, null, null);

        public static UsageScope ofAllies(Collection<Long> allyIds) {
            return new UsageScope(allyIds, null, null);
        }

        public static UsageScope ofMember(Long memberId) {
            return new UsageScope(null, memberId, null);
        }

        public static UsageScope ofBeneficiary(Long beneficiaryId) {
            return new UsageScope(null, null, beneficiaryId);
        }

        String where(MapSqlParameterSource params) {
            StringBuilder sql = new StringBuilder();
            if (allyIds != null) {
                sql.append(" AND bu.ally_id IN (:allyIds)");
                params.addValue("allyIds", allyIds);
            }
            if (memberId != null) {
                sql.append(" AND ms.member_id = :memberId");
                params.addValue("memberId", memberId);
            }
            if (beneficiaryId != null) {
                sql.append(" AND bu.beneficiary_id = :beneficiaryId");
                params.addValue("beneficiaryId", beneficiaryId);
            }
            return sql.toString();
        }
    }

    // ─── Projections ──────────────────────────────────────────────────────────

    /** Approved collections of one month in one currency (net of discounts). */
    public record MonthlyAmount(String month, String currencyCode, BigDecimal amount) {}

    /** Usage totals in a date range: rows and distinct people served. */
    public record UsageTotals(long usages, long peopleServed) {}

    /** A labelled count (service, person) — {@code key} disambiguates equal labels. */
    public record LabelledCount(String key, String label, long count) {}

    public record PaymentEvent(String status, String personName, String planName, BigDecimal amount,
                               String currencyCode, Instant occurredAt) {}

    public record UsageEvent(String status, String personName, String allyName, String serviceName,
                             Instant occurredAt) {}

    public record AffiliationRequestEvent(String status, String applicantName, String planName, Instant occurredAt) {}

    public record EnrollmentEvent(String memberName, String planName, Instant occurredAt) {}

    // ─── Members / memberships ────────────────────────────────────────────────

    /** Active members whose current membership is ACTIVE. */
    public long countMembersWithActiveMembership() {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT ms.member_id)
                FROM memberships ms
                JOIN members mb ON mb.members_id = ms.member_id
                WHERE ms.is_active AND ms.status = 'ACTIVE' AND mb.is_active
                """, new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Members by the status of their latest membership (the current one when
     * present, else the most recent) — one row per member, so a member that
     * was canceled and re-enrolled is counted once.
     */
    public Map<String, Long> countMembershipsByStatus() {
        return countMap("""
                SELECT s.status, COUNT(*) FROM (
                    SELECT DISTINCT ON (ms.member_id) ms.status
                    FROM memberships ms
                    JOIN members mb ON mb.members_id = ms.member_id
                    WHERE mb.is_active
                    ORDER BY ms.member_id, ms.is_active DESC, ms.enrolled_at DESC, ms.memberships_id DESC
                ) s
                GROUP BY s.status
                """, new MapSqlParameterSource());
    }

    /** New members per {@code yyyy-MM} of {@code enrolled_at} since {@code from}; optionally one advisor's. */
    public Map<String, Long> countNewMembersByMonth(LocalDate from, Long promoterId) {
        MapSqlParameterSource params = new MapSqlParameterSource("from", from);
        String scope = "";
        if (promoterId != null) {
            scope = " AND mb.promoter_id = :promoterId";
            params.addValue("promoterId", promoterId);
        }
        return countMap("""
                SELECT to_char(mb.enrolled_at, 'YYYY-MM'), COUNT(*)
                FROM members mb
                WHERE mb.is_active AND mb.enrolled_at >= :from""" + scope + """

                GROUP BY 1
                """, params);
    }

    // ─── Payments ─────────────────────────────────────────────────────────────

    /** Approved incoming collections per month (by approval date) and currency since {@code from}. */
    public List<MonthlyAmount> sumApprovedCollectionsByMonth(Instant from) {
        MapSqlParameterSource params = new MapSqlParameterSource("from", Timestamp.from(from))
                .addValue("tz", AppTimeZone.ZONE.getId());
        return jdbc.query("""
                SELECT to_char(COALESCE(p.reviewed_at, p.received_at) AT TIME ZONE :tz, 'YYYY-MM') AS month,
                       c.code,
                       SUM(p.amount - COALESCE(p.discount_amount, 0)) AS total
                FROM payments p
                JOIN currencies c ON c.currencies_id = p.currency_id
                WHERE p.is_active AND p.direction = 'IN' AND p.status = 'APPROVED'
                  AND COALESCE(p.reviewed_at, p.received_at) >= :from
                GROUP BY 1, 2
                """, params, (rs, i) -> new MonthlyAmount(rs.getString(1), rs.getString(2), rs.getBigDecimal(3)));
    }

    /** Incoming collections waiting for review. */
    public long countPendingCollections() {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM payments p
                WHERE p.is_active AND p.direction = 'IN' AND p.status = 'PENDING'
                """, new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Latest incoming collections (registered, approved or rejected), newest
     * event first; optionally of one advisor's network or one payer.
     */
    public List<PaymentEvent> findRecentPayments(Long promoterId, Long personId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        StringBuilder scope = new StringBuilder();
        if (promoterId != null) {
            scope.append(" AND p.promoter_id = :promoterId");
            params.addValue("promoterId", promoterId);
        }
        if (personId != null) {
            scope.append(" AND p.person_id = :personId");
            params.addValue("personId", personId);
        }
        return jdbc.query("""
                SELECT p.status, pe.full_name, pl.name, p.amount - COALESCE(p.discount_amount, 0), c.code,
                       COALESCE(p.reviewed_at, p.received_at) AS occurred_at
                FROM payments p
                JOIN persons pe ON pe.persons_id = p.person_id
                JOIN currencies c ON c.currencies_id = p.currency_id
                LEFT JOIN memberships ms ON ms.memberships_id = p.membership_id
                LEFT JOIN plans pl ON pl.plans_id = ms.plan_id
                WHERE p.is_active AND p.direction = 'IN' AND p.status IN ('PENDING', 'APPROVED', 'REJECTED')""" + scope + """

                ORDER BY occurred_at DESC
                LIMIT :limit
                """, params, (rs, i) -> new PaymentEvent(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4), rs.getString(5), instant(rs, 6)));
    }

    // ─── Benefit usages ───────────────────────────────────────────────────────

    /** Live usages and distinct people served with {@code usage_date} in [{@code from}, {@code to}]. */
    public UsageTotals usageTotals(UsageScope scope, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource("from", from).addValue("to", to);
        String where = scope.where(params);
        return jdbc.queryForObject("""
                SELECT COUNT(*),
                       COUNT(DISTINCT CASE WHEN bu.beneficiary_id IS NULL THEN 'H' || ms.member_id
                                           ELSE 'B' || bu.beneficiary_id END)
                FROM benefit_usages bu
                JOIN memberships ms ON ms.memberships_id = bu.membership_id
                WHERE """ + LIVE_USAGE + " AND bu.usage_date BETWEEN :from AND :to" + where,
                params, (rs, i) -> new UsageTotals(rs.getLong(1), rs.getLong(2)));
    }

    /** Live usages per {@code yyyy-MM} of {@code usage_date} since {@code from}. */
    public Map<String, Long> countUsagesByMonth(UsageScope scope, LocalDate from) {
        MapSqlParameterSource params = new MapSqlParameterSource("from", from);
        String where = scope.where(params);
        return countMap("""
                SELECT to_char(bu.usage_date, 'YYYY-MM'), COUNT(*)
                FROM benefit_usages bu
                JOIN memberships ms ON ms.memberships_id = bu.membership_id
                WHERE """ + LIVE_USAGE + " AND bu.usage_date >= :from" + where + " GROUP BY 1", params);
    }

    /** Most used services (catalog product, else category) since {@code from}. */
    public List<LabelledCount> topServices(UsageScope scope, LocalDate from, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("from", from).addValue("limit", limit);
        String where = scope.where(params);
        return jdbc.query("""
                SELECT COALESCE('P' || pr.products_id, 'C' || pc.product_categories_id),
                       COALESCE(pr.name, pc.name), COUNT(*)
                FROM benefit_usages bu
                JOIN memberships ms ON ms.memberships_id = bu.membership_id
                JOIN product_categories pc ON pc.product_categories_id = bu.product_category_id
                LEFT JOIN ally_products ap ON ap.ally_products_id = bu.ally_product_id
                LEFT JOIN products pr ON pr.products_id = ap.product_id
                WHERE """ + LIVE_USAGE + " AND bu.usage_date >= :from" + where + """

                GROUP BY 1, 2
                ORDER BY 3 DESC, 2
                LIMIT :limit
                """, params, (rs, i) -> new LabelledCount(rs.getString(1), rs.getString(2), rs.getLong(3)));
    }

    /** Live usages per person of a family: the holder ({@code key = H}) and each beneficiary. */
    public List<LabelledCount> countUsagesByPerson(Long memberId) {
        MapSqlParameterSource params = new MapSqlParameterSource("memberId", memberId);
        return jdbc.query("""
                SELECT COALESCE('B' || b.beneficiaries_id, 'H'), COALESCE(bp.full_name, hp.full_name), COUNT(*)
                FROM benefit_usages bu
                JOIN memberships ms ON ms.memberships_id = bu.membership_id
                JOIN members mb ON mb.members_id = ms.member_id
                JOIN persons hp ON hp.persons_id = mb.person_id
                LEFT JOIN beneficiaries b ON b.beneficiaries_id = bu.beneficiary_id
                LEFT JOIN persons bp ON bp.persons_id = b.person_id
                WHERE """ + LIVE_USAGE + """
                 AND ms.member_id = :memberId
                GROUP BY 1, 2
                ORDER BY 3 DESC, 2
                """, params, (rs, i) -> new LabelledCount(rs.getString(1), rs.getString(2), rs.getLong(3)));
    }

    /** Latest usages (registered or reversed), newest first. */
    public List<UsageEvent> findRecentUsages(UsageScope scope, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        String where = scope.where(params);
        return jdbc.query("""
                SELECT bu.status, COALESCE(bp.full_name, hp.full_name), COALESCE(a.trade_name, a.name),
                       COALESCE(pr.name, pc.name), bu.usage_datetime
                FROM benefit_usages bu
                JOIN memberships ms ON ms.memberships_id = bu.membership_id
                JOIN members mb ON mb.members_id = ms.member_id
                JOIN persons hp ON hp.persons_id = mb.person_id
                LEFT JOIN beneficiaries b ON b.beneficiaries_id = bu.beneficiary_id
                LEFT JOIN persons bp ON bp.persons_id = b.person_id
                JOIN allies a ON a.allies_id = bu.ally_id
                JOIN product_categories pc ON pc.product_categories_id = bu.product_category_id
                LEFT JOIN ally_products ap ON ap.ally_products_id = bu.ally_product_id
                LEFT JOIN products pr ON pr.products_id = ap.product_id
                WHERE bu.status IN ('REGISTERED', 'REVERSED')""" + where + """

                ORDER BY bu.usage_datetime DESC
                LIMIT :limit
                """, params, (rs, i) -> new UsageEvent(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), instant(rs, 5)));
    }

    // ─── Affiliation requests ─────────────────────────────────────────────────

    public long countPendingAffiliationRequests() {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM affiliation_requests ar
                WHERE ar.is_active AND ar.status = 'PENDING'
                """, new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /** Latest affiliation requests by their last event (received or reviewed); optionally one advisor's QR. */
    public List<AffiliationRequestEvent> findRecentAffiliationRequests(Long promoterId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        String scope = "";
        if (promoterId != null) {
            scope = " AND ar.promoter_id = :promoterId";
            params.addValue("promoterId", promoterId);
        }
        return jdbc.query("""
                SELECT ar.status, ar.first_name || ' ' || ar.last_name, pl.name,
                       COALESCE(ar.reviewed_at, ar.created_at) AS occurred_at
                FROM affiliation_requests ar
                JOIN plans pl ON pl.plans_id = ar.plan_id
                WHERE ar.is_active""" + scope + """

                ORDER BY occurred_at DESC
                LIMIT :limit
                """, params, (rs, i) -> new AffiliationRequestEvent(rs.getString(1), rs.getString(2),
                rs.getString(3), instant(rs, 4)));
    }

    /** Latest members enrolled in an advisor's portfolio. */
    public List<EnrollmentEvent> findRecentEnrollments(Long promoterId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("promoterId", promoterId).addValue("limit", limit);
        return jdbc.query("""
                SELECT pe.full_name, pl.name, mb.created_at
                FROM members mb
                JOIN persons pe ON pe.persons_id = mb.person_id
                LEFT JOIN memberships ms ON ms.member_id = mb.members_id AND ms.is_active
                LEFT JOIN plans pl ON pl.plans_id = ms.plan_id
                WHERE mb.is_active AND mb.promoter_id = :promoterId
                ORDER BY mb.created_at DESC
                LIMIT :limit
                """, params, (rs, i) -> new EnrollmentEvent(rs.getString(1), rs.getString(2), instant(rs, 3)));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Long> countMap(String sql, MapSqlParameterSource params) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        });
        return result;
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }
}
