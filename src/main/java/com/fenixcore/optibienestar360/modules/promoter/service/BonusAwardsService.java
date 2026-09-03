package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.promoter.dto.BonusAwardDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterBonusAward;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterBonusAwardRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read queues over {@link PromoterBonusAward} (v2 PDF #5). Plural-name
 * convention matches {@code CommissionsService} — the admin queue + the promoter
 * self-service list; the write side (granting) is the singular
 * {@code BonusEvaluationService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BonusAwardsService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "status", "rewardType", "rewardCurrency", "amount", "blocksAwarded",
            "metricCount", "windowStart", "windowEnd", "evaluatedAt", "paidAt",
            "createdAt", "active", "promoter.uuid", "rule.uuid"
    );

    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(PromoterBonusAward.class, Map.of(
                    "rule_Display", "rule.name",
                    "promoter_Display", "promoter.displayName"
            ));

    private static final String[] SEARCHABLE_FIELDS = {
            "ruleNameSnapshot", "payoutReference", "adminNotes"
    };

    private final PromoterBonusAwardRepository repository;
    private final PromoterRepository promoterRepository;
    private final DefaultSortResolver defaultSortResolver;

    // ─── Admin queue ──────────────────────────────────────────────────────────

    public BonusAwardDto get(UUID uuid) {
        return BonusAwardDto.from(repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("bonus_award.not_found")));
    }

    public Page<BonusAwardDto> list(Pageable pageable, String filter, String q) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "bonus_award", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "bonus_award");
        Specification<PromoterBonusAward> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "bonus_award.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, resolvedPageable).map(BonusAwardDto::from);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("bonus_award", pageable);
    }

    // ─── Promoter self-service ────────────────────────────────────────────────

    /** A promoter's own awards, newest first. 404 when the caller is not a promoter. */
    public Page<BonusAwardDto> listOwn(UUID userUuid, Pageable pageable) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        return repository.findByPromoterIdAndActiveTrueOrderByCreatedAtDesc(promoter.getId(), pageable)
                .map(BonusAwardDto::from);
    }

    private static Specification<PromoterBonusAward> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
