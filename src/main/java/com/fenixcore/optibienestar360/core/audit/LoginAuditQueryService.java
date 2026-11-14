package com.fenixcore.optibienestar360.core.audit;

import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.audit.dto.LoginAuditLogDto;
import com.fenixcore.optibienestar360.core.audit.entity.LoginAuditLog;
import com.fenixcore.optibienestar360.core.audit.repository.LoginAuditLogRepository;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Backs {@code GET /v1/admin/audit/logins} (spec 16-audit.md §Endpoints
 * admin) — every login attempt (success and failure) plus session lifecycle,
 * filterable by user, result and date range.
 */
@Service
@RequiredArgsConstructor
public class LoginAuditQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    /**
     * Window a free-text {@code ?q=} search is bounded to when the caller gives no
     * {@code from}. The search is a {@code LIKE '%…%'} that no index can serve, and
     * this table gets a row per login attempt: unbounded, it would read the whole
     * history on every keystroke. Bounded, the planner reaches the window through
     * {@code idx_login_audit_attempted_at} and only scans those rows. An explicit
     * {@code from} (or an RSQL date filter) still searches any range.
     */
    static final Duration DEFAULT_SEARCH_WINDOW = Duration.ofDays(90);

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "attemptedEmail", "result", "userId", "sessionStatus", "valid", "attemptedAt", "createdAt"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(LoginAuditLog.class, Map.of());

    private final LoginAuditLogRepository loginAuditLogRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final DefaultSortResolver defaultSortResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<LoginAuditLogDto> list(Pageable pageable, UUID uuid, String email, UUID userUuid, LoginAuditResult result,
                                        Instant from, Instant to, String filter) {
        return list(pageable, uuid, email, userUuid, result, from, to, filter, null);
    }

    /** Same as above plus a free-text {@code q} (accent/case-insensitive) over email, IP, host, agent and failure reason. */
    public Page<LoginAuditLogDto> list(Pageable pageable, UUID uuid, String email, UUID userUuid, LoginAuditResult result,
                                        Instant from, Instant to, String filter, String q) {
        if (pageable.isPaged() && pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("audit.page.size.exceeded");
        }

        Specification<LoginAuditLog> spec = (root, query, cb) -> cb.conjunction();

        if (uuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("uuid"), uuid));
        }
        if (email != null && !email.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("attemptedEmail"), email));
        }
        if (result != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("result"), result));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("attemptedAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("attemptedAt"), to));
        }
        if (userUuid != null) {
            Long userId = userRepository.findByUuid(userUuid).map(User::getId).orElse(-1L);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "audit.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }

        if (q != null && !q.isBlank()) {
            // ipAddress is left out: casting inet to text rules out any index, and
            // an IP is searched exactly with the RSQL filter instead.
            spec = spec.and(SearchSpecifications.acrossFields(q,
                    "attemptedEmail", "hostname", "userAgent", "failureReason"));
            if (from == null) {
                Instant windowStart = Instant.now(clock).minus(DEFAULT_SEARCH_WINDOW);
                spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("attemptedAt"), windowStart));
            }
        }

        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("login_audit_log", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "login_audit_log");
        return loginAuditLogRepository.findAll(spec, resolvedPageable).map(this::toDto);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("login_audit_log", pageable);
    }

    private LoginAuditLogDto toDto(LoginAuditLog log) {
        UUID userUuid = log.getUserId() != null
                ? userRepository.findById(log.getUserId()).map(User::getUuid).orElse(null)
                : null;

        return new LoginAuditLogDto(
                log.getUuid(),
                userUuid,
                log.getAttemptedEmail(),
                log.getResult(),
                resolveResultDisplay(log.getResult()),
                log.getRoles(),
                log.getLocale(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getHostname(),
                log.getFailureReason(),
                log.getSessionStatus(),
                log.getSessionExpiresAt(),
                log.isValid(),
                log.getLoggedOutAt(),
                log.getLogoutReason(),
                log.getAttemptedAt()
        );
    }

    private String resolveResultDisplay(LoginAuditResult result) {
        if (result == null) {
            return null;
        }
        try {
            return messageSource.getMessage("audit.login.result." + result.name(), null, LocaleContextHolder.getLocale());
        } catch (Exception ex) {
            return null;
        }
    }
}
