package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.promoter.dto.MyReferralDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Referral;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.fenixcore.optibienestar360.modules.promoter.mapper.ReferralMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.ReferralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-side service over {@code referrals}. Plural-name convention
 * matches {@code PaymentsService} / {@code CommissionsService} — the
 * singular {@link ReferralService} owns the write engine (register +
 * apply reward) and this one owns read endpoints.
 *
 * <p>v1 exposes only the affiliate's own view ({@link #listForUser}).
 * When an admin queue endpoint lands later, the {@code list(Pageable,
 * filter, q)} method drops in alongside, same RSQL pattern the
 * Commission / Payment services use.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferralsService {

    private final ReferralRepository repository;
    private final ReferralMapper mapper;

    /**
     * Powers {@code GET /v1/me/referrals}. Returns the page of referrals
     * where the JWT-resolved user is the REFERRER. No filter / no
     * free-text — this surface is the full history, not a curated query.
     */
    public Page<MyReferralDto> listForUser(UUID userUuid, Pageable pageable) {
        return repository.findOwnByUserUuid(userUuid, pageable).map(mapper::toMyDto);
    }

    /**
     * Same feed narrowed by {@code status} and a free-text {@code q} (accent/case-insensitive) over the
     * referred friend's name/document and the referral code. Blank filters fall back to the plain feed.
     * The referred member is nullable (friend not yet enrolled), so it is searched through an explicit
     * LEFT JOIN — navigating it with {@code get()} would drop those rows.
     */
    public Page<MyReferralDto> listForUser(UUID userUuid, String status, String q, Pageable pageable) {
        return listForUser(userUuid, status, q, null, null, pageable);
    }

    /** {@code dateFrom}/{@code dateTo}: inclusive Caracas-day range on the day the referral was registered ({@code createdAt}). */
    public Page<MyReferralDto> listForUser(UUID userUuid, String status, String q,
                                           java.time.LocalDate dateFrom, java.time.LocalDate dateTo, Pageable pageable) {
        boolean noStatus = status == null || status.isBlank();
        boolean noQuery = q == null || q.isBlank();
        if (noStatus && noQuery && dateFrom == null && dateTo == null) {
            return listForUser(userUuid, pageable);
        }
        Specification<Referral> spec = (root, query, cb) -> {
            Subquery<Long> own = query.subquery(Long.class);
            Root<Member> member = own.from(Member.class);
            Subquery<Long> personId = query.subquery(Long.class);
            Root<User> user = personId.from(User.class);
            personId.select(user.get("person").get("id")).where(cb.equal(user.get("uuid"), userUuid));
            own.select(member.get("id")).where(cb.equal(member.get("person").get("id"), personId));
            return cb.and(cb.isTrue(root.get("active")), cb.equal(root.get("referrer").get("id"), own));
        };
        if (!noStatus) {
            String normalized = status.trim().toUpperCase();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), normalized));
        }
        if (!noQuery) {
            spec = spec.and((root, query, cb) -> {
                Join<Referral, ?> referred = root.join("referred", JoinType.LEFT);
                Join<?, ?> person = referred.join("person", JoinType.LEFT);
                return cb.or(
                        SearchSpecifications.anyLike(cb, root, q, "referralCode"),
                        SearchSpecifications.anyLike(cb, person, q, "fullName", "documentNumber"));
            });
        }
        spec = spec.and(com.fenixcore.optibienestar360.core.util.DateRangeSpecifications.instantBetween("createdAt", dateFrom, dateTo));
        return repository.findAll(spec, pageable).map(mapper::toMyDto);
    }
}
