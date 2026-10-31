package com.fenixcore.optibienestar360.modules.benefit.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageRatingRequest;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage.UsageStatus;
import com.fenixcore.optibienestar360.modules.benefit.mapper.BenefitUsageMapper;
import com.fenixcore.optibienestar360.modules.benefit.repository.BenefitUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The affiliate rates their own benefit usages (V201): stars for the ally and,
 * when the usage was of a catalogued offer, for the product. The rating lives
 * on the usage row, so only someone who actually consumed can rate, once per
 * usage (re-rating replaces it).
 *
 * <p>Every rating recomputes the denormalized averages the public directory
 * and catalog read: the ally's, the offer's and the product's (cross-ally).</p>
 */
@Service
@RequiredArgsConstructor
public class BenefitUsageRatingsService {

    private final BenefitUsageRepository usageRepository;
    private final BenefitUsageMapper mapper;

    @Transactional
    @Auditable(entity = "benefit_usage", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public BenefitUsageDto rate(UUID userUuid, UUID usageUuid, BenefitUsageRatingRequest request) {
        BenefitUsage usage = usageRepository.findByUuidForMemberUser(usageUuid, userUuid)
                .orElseThrow(() -> new NoSuchElementException("benefit_usage.not_found"));
        // A reversed or disputed usage doesn't count toward the averages, so
        // rating it would be a vote that silently goes nowhere.
        if (!UsageStatus.REGISTERED.name().equals(usage.getStatus())) {
            throw new IllegalArgumentException("benefit_usage.rating.not_ratable");
        }
        AllyProduct offer = usage.getAllyProduct();
        if (request.productRating() != null && offer == null) {
            throw new IllegalArgumentException("benefit_usage.rating.product_not_applicable");
        }

        usage.setAllyRating(request.allyRating().shortValue());
        usage.setProductRating(request.productRating() == null ? null : request.productRating().shortValue());
        usage.setRatingComment(blankToNull(request.comment()));
        usage.setRatedAt(Instant.now());
        usageRepository.save(usage);

        usageRepository.refreshAllyRating(usage.getAlly().getId());
        if (offer != null) {
            usageRepository.refreshAllyProductRating(offer.getId());
            usageRepository.refreshProductRating(offer.getProduct().getId());
        }
        return mapper.toDto(usage);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
