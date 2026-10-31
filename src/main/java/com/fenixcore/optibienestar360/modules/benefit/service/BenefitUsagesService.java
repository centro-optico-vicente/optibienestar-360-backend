package com.fenixcore.optibienestar360.modules.benefit.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageDto;
import com.fenixcore.optibienestar360.modules.benefit.dto.BenefitUsageRegisterRequest;
import com.fenixcore.optibienestar360.modules.benefit.dto.UsagePersonSummaryDto;
import com.fenixcore.optibienestar360.modules.benefit.dto.UsageServiceSummaryDto;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage;
import com.fenixcore.optibienestar360.modules.benefit.entity.BenefitUsage.UsageStatus;
import com.fenixcore.optibienestar360.modules.benefit.mapper.BenefitUsageMapper;
import com.fenixcore.optibienestar360.modules.benefit.repository.BenefitUsageRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.ProductCategory;
import com.fenixcore.optibienestar360.modules.catalog.repository.ProductCategoryRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Application service for {@link BenefitUsage}. This commit ships only
 * the registration entry point; the history-read endpoint
 * ({@code GET /v1/me/allies/usage-history}) lands in a separate bullet.
 *
 * <p>Plural-name convention ({@code BenefitUsagesService}) matches
 * {@code PlansService}, {@code MembershipsService}, {@code PaymentsService}.</p>
 *
 * <p><b>Authority assumed:</b> the validator endpoint
 * ({@code GET /v1/me/allies/validations/{document}}) is the ally portal's
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
    private final AllyProductRepository allyProductRepository;
    private final AllyUserRepository allyUserRepository;
    private final CurrencyRepository currencyRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final BenefitUsageMapper mapper;

    /**
     * Powers {@code GET /v1/me/allies/usage-history}. Returns every benefit
     * usage registered at any ally the current user is an active operator
     * on (handles the N:M {@code AllyUser} pivot through a JPQL subquery).
     * Read-only, no scope check beyond the repository query — the JPQL
     * naturally restricts results to the user's own allies.
     */
    public Page<BenefitUsageDto> listForAllyUser(UUID userUuid, Pageable pageable) {
        return usageRepository.findByAllyUserUuid(userUuid, pageable).map(mapper::toDto);
    }

    /**
     * Same as {@link #listForAllyUser} but filtered by the member's document
     * number or full name. A blank {@code search} falls back to the
     * unfiltered query so callers don't need to branch.
     */
    public Page<BenefitUsageDto> listForAllyUser(UUID userUuid, String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return listForAllyUser(userUuid, pageable);
        }
        return usageRepository.findByAllyUserUuidAndSearch(userUuid, search.trim(), pageable).map(mapper::toDto);
    }

    /**
     * Powers {@code GET /v1/me/usage-history} (vertical-9). The member's own
     * benefit-consumption feed across every ally, scoped to the caller via the
     * {@code user.person → member.person → membership} chain in the repository
     * query. Read-only, newest usage first (caller's Pageable default).
     */
    public Page<BenefitUsageDto> listForMemberUser(UUID userUuid, Pageable pageable) {
        return usageRepository.findByMemberUserUuid(userUuid, pageable).map(mapper::toDto);
    }

    /**
     * {@link #listForMemberUser} narrowed by who consumed (holder or one
     * beneficiary) and by service. Without filters it is the plain feed.
     */
    public Page<BenefitUsageDto> listForMemberUser(UUID userUuid, UsageFilter filter, Pageable pageable) {
        if (filter.isEmpty()) {
            return listForMemberUser(userUuid, pageable);
        }
        return usageRepository.findByMemberUserUuidFiltered(userUuid, filter.holderOnly(), filter.beneficiaryUuid(),
                filter.productUuid(), filter.productCategoryUuid(), pageable).map(mapper::toDto);
    }

    /** Powers {@code GET /v1/me/usage-history/summary}: consumption grouped by service. */
    public List<UsageServiceSummaryDto> summarizeForMemberUser(UUID userUuid, boolean holderOnly, UUID beneficiaryUuid) {
        return usageRepository.summarizeByServiceForMemberUser(userUuid, holderOnly, beneficiaryUuid);
    }

    /** Powers {@code GET /v1/me/usage-history/by-person}: consumption count per family member. */
    public List<UsagePersonSummaryDto> summarizeByPersonForMemberUser(UUID userUuid) {
        return usageRepository.summarizeByPersonForMemberUser(userUuid);
    }

    /** Optional filters of the affiliate's consumption history. */
    public record UsageFilter(boolean holderOnly, UUID beneficiaryUuid, UUID productUuid, UUID productCategoryUuid) {
        public boolean isEmpty() {
            return !holderOnly && beneficiaryUuid == null && productUuid == null && productCategoryUuid == null;
        }
    }

    @Transactional
    @Auditable(entity = "benefit_usage", action = AuditAction.CREATE)
    public BenefitUsageDto register(BenefitUsageRegisterRequest request) {
        Membership membership = membershipRepository.findByUuid(request.membershipUuid())
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));
        ensureActiveMembership(membership);

        Ally ally = allyRepository.findByUuid(request.allyUuid())
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));

        AllyProduct allyProduct = null;
        if (request.allyProductUuid() != null) {
            allyProduct = allyProductRepository.findByUuid(request.allyProductUuid())
                    .orElseThrow(() -> new NoSuchElementException("ally_product.not_found"));
        }

        AllyUser allyUser = null;
        if (request.allyUserUuid() != null) {
            allyUser = allyUserRepository.findByUuid(request.allyUserUuid())
                    .orElseThrow(() -> new NoSuchElementException("ally_user.not_found"));
        }

        validateCopay(request);
        validateConsumption(request);

        BenefitUsage usage = new BenefitUsage();
        usage.setMembership(membership);
        usage.setAlly(ally);
        usage.setAllyProduct(allyProduct);
        usage.setAllyUser(allyUser);
        usage.setBeneficiary(resolveBeneficiary(request.beneficiaryUuid(), membership));
        usage.setProductCategory(resolveProductCategory(request, allyProduct));
        usage.setUsageDate(request.usageDate() != null ? request.usageDate() : LocalDate.now());
        usage.setUsageDatetime(Instant.now());
        usage.setCopayAmount(request.copayAmount());
        usage.setCopayCurrency(resolveCurrency(request.copayCurrency()));
        usage.setConsumptionAmount(request.consumptionAmount());
        usage.setConsumptionCurrency(resolveCurrency(request.consumptionCurrency()));
        usage.setMetadata(request.metadata());
        usage.setNotes(request.notes());
        usage.setStatus(UsageStatus.REGISTERED.name());

        return mapper.toDto(usageRepository.save(usage));
    }

    /**
     * The beneficiary who consumed, or {@code null} for the holder. It must be
     * an active beneficiary of the membership's own member — anyone else is a
     * 404, so a usage can never be booked on another family's person.
     */
    private Beneficiary resolveBeneficiary(UUID beneficiaryUuid, Membership membership) {
        if (beneficiaryUuid == null) return null;
        return beneficiaryRepository.findByUuid(beneficiaryUuid)
                .filter(Beneficiary::isActive)
                .filter(b -> b.getMember().getId().equals(membership.getMember().getId()))
                .orElseThrow(() -> new NoSuchElementException("beneficiary.not_found"));
    }

    /** Both money columns are nullable and paired with their amount (V24 / V158 CHECKs). */
    private Currency resolveCurrency(String code) {
        if (code == null || code.isBlank()) return null;
        return currencyRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
    }

    /**
     * The category is mandatory on the row (V158) but the client only has to
     * send it when there is no catalogued service: a service already carries
     * its own category, so copying it avoids asking the operator twice — and
     * avoids the two disagreeing. An explicit category is ignored when a
     * service is present, for that same reason.
     */
    private ProductCategory resolveProductCategory(BenefitUsageRegisterRequest request, AllyProduct allyProduct) {
        if (allyProduct != null) {
            return allyProduct.getProduct().getProductCategory();
        }
        if (request.productCategoryUuid() == null) {
            throw new IllegalArgumentException("benefit_usage.product_category.required");
        }
        return productCategoryRepository.findByUuid(request.productCategoryUuid())
                .orElseThrow(() -> new NoSuchElementException("product_category.not_found"));
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

    /** Same pairing rule as the co-pay, against V158's own CHECK. */
    private static void validateConsumption(BenefitUsageRegisterRequest request) {
        boolean amountSet = request.consumptionAmount() != null;
        boolean currencySet = request.consumptionCurrency() != null && !request.consumptionCurrency().isBlank();
        if (amountSet ^ currencySet) {
            throw new IllegalArgumentException("benefit_usage.consumption.incomplete");
        }
    }
}
