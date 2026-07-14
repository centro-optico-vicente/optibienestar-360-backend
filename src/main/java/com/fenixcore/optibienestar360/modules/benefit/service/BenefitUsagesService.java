package com.fenixcore.optibienestar360.modules.benefit.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageRegisterRequest;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage.UsageStatus;
import com.fenixcore.optibienestar360.modules.benefit.mapper.BenefitUsageMapper;
import com.fenixcore.optibienestar360.modules.benefit.repository.BenefitUsageRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Application service for {@link BenefitUsage}. This commit ships only
 * the registration entry point; the history-read endpoint
 * ({@code GET /v1/ally/usage-history}) lands in a separate bullet.
 *
 * <p>Plural-name convention ({@code BenefitUsagesService}) matches
 * {@code PlansService}, {@code MembershipsService}, {@code PaymentsService}.</p>
 *
 * <p><b>Authority assumed:</b> the validator endpoint
 * ({@code GET /v1/ally/validate/{document}}) is the ally portal's
 * pre-condition. We still re-check {@link Membership#getStatus()} here so a
 * stale request (or an unauthorized POST that skipped the validator) can't
 * register a usage against a non-ACTIVE membership.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BenefitUsagesService {

    private final BenefitUsageRepository usageRepository;
    private final MembershipRepository membershipRepository;
    private final AllyRepository allyRepository;
    private final AllyServiceRepository allyServiceRepository;
    private final AllyUserRepository allyUserRepository;
    private final BenefitUsageMapper mapper;

    /**
     * Powers {@code GET /v1/ally/usage-history}. Returns every benefit
     * usage registered at any ally the current user is an active operator
     * on (handles the N:M {@code AllyUser} pivot through a JPQL subquery).
     * Read-only, no scope check beyond the repository query — the JPQL
     * naturally restricts results to the user's own allies.
     */
    public Page<BenefitUsageDto> listForAllyUser(UUID userUuid, Pageable pageable) {
        return usageRepository.findByAllyUserUuid(userUuid, pageable).map(mapper::toDto);
    }

    @Transactional
    public BenefitUsageDto register(BenefitUsageRegisterRequest request) {
        Membership membership = membershipRepository.findByUuid(request.membershipUuid())
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));
        ensureActiveMembership(membership);

        Ally ally = allyRepository.findByUuid(request.allyUuid())
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));

        AllyService allyService = null;
        if (request.allyServiceUuid() != null) {
            allyService = allyServiceRepository.findByUuid(request.allyServiceUuid())
                    .orElseThrow(() -> new NoSuchElementException("ally_service.not_found"));
        }

        AllyUser allyUser = null;
        if (request.allyUserUuid() != null) {
            allyUser = allyUserRepository.findByUuid(request.allyUserUuid())
                    .orElseThrow(() -> new NoSuchElementException("ally_user.not_found"));
        }

        validateCopay(request);

        BenefitUsage usage = new BenefitUsage();
        usage.setMembership(membership);
        usage.setAlly(ally);
        usage.setAllyService(allyService);
        usage.setAllyUser(allyUser);
        usage.setUsageDate(request.usageDate() != null ? request.usageDate() : LocalDate.now());
        usage.setUsageDatetime(Instant.now());
        usage.setCopayAmount(request.copayAmount());
        usage.setCopayCurrency(request.copayCurrency());
        usage.setMetadata(request.metadata());
        usage.setNotes(request.notes());
        usage.setStatus(UsageStatus.REGISTERED.name());

        return mapper.toDto(usageRepository.save(usage));
    }

    private static void ensureActiveMembership(Membership membership) {
        if (membership == null || !membership.isActive()) {
            throw new IllegalArgumentException("benefit_usage.membership.not_active");
        }
        if (!LifecycleStatus.ACTIVE.name().equals(membership.getStatus())) {
            throw new IllegalArgumentException("benefit_usage.membership.not_active");
        }
    }

    /**
     * Mirrors the V24 CHECK constraint
     * {@code chk_benefit_usages_copay_paired} — amount and currency move
     * together. Pre-checking at the service yields a clean 422
     * {@code benefit_usage.copay.incomplete} instead of a misleading 409
     * from the DB constraint.
     */
    private static void validateCopay(BenefitUsageRegisterRequest request) {
        boolean amountSet = request.copayAmount() != null;
        boolean currencySet = request.copayCurrency() != null && !request.copayCurrency().isBlank();
        if (amountSet ^ currencySet) {
            throw new IllegalArgumentException("benefit_usage.copay.incomplete");
        }
    }
}
