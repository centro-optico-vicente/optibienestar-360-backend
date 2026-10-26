package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductDto;
import com.fenixcore.optibienestar360.modules.ally.dto.ProposeAllyProductRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.product.entity.Product;
import com.fenixcore.optibienestar360.modules.product.service.ProductsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Ally-side proposal flow for {@link AllyProduct} offers (hub ADR 0026).
 * Backs {@code POST /v1/me/allies/products}: the ally either offers an
 * existing general product ({@code productUuid}) or proposes a new item,
 * which becomes a product exclusive to that ally.
 *
 * <p>Authorization is membership-based, NOT just permission-based: any
 * authenticated user can hit the endpoint, but the service rejects with
 * 403 unless the caller has an <b>active</b> {@link AllyUser} membership
 * on the target ally with {@code allyRole} in (OWNER, STAFF). VIEWER is
 * intentionally excluded — read-only roles can't propose.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyProductsProposeService {

    private final AllyUserRepository allyUserRepository;
    private final AllyProductRepository offerRepository;
    private final CurrencyRepository currencyRepository;
    private final ProductsService productsService;
    private final AllyMapper mapper;

    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    /**
     * Creates a new offer on behalf of the actor under the requested ally.
     * It lands at {@code reviewStatus=PROPOSED} — admin moves it via the
     * workflow endpoints so the review log captures the transition.
     *
     * @throws AccessDeniedException if the actor has no active OWNER/STAFF
     *         membership on the target ally.
     * @throws NoSuchElementException if the product or category UUID doesn't resolve.
     * @throws IllegalArgumentException if the product is exclusive to another
     *         ally, or the ally already offers it.
     */
    @Transactional
    public AllyProductDto propose(UUID actorUserUuid, ProposeAllyProductRequest req) {
        AllyUser membership = allyUserRepository
                .findActiveByAllyUuidAndUserUuid(req.allyUuid(), actorUserUuid)
                .orElseThrow(() -> new AccessDeniedException("ally_product.propose.not_allowed"));

        if (membership.getAllyRole() == AllyRole.VIEWER) {
            // VIEWER can read but never propose.
            throw new AccessDeniedException("ally_product.propose.not_allowed");
        }

        Ally ally = membership.getAlly();  // reuse the already-loaded managed ally
        Product product = req.productUuid() != null
                ? productsService.requireOfferable(req.productUuid(), ally)
                : productsService.createExclusive(ally, req.productCategoryUuid(), req.productType(),
                        req.name(), req.description(), req.requiresAppointment());
        if (product.getId() != null && offerRepository.existsByAllyIdAndProductId(ally.getId(), product.getId())) {
            throw new IllegalArgumentException("ally_product.already_offered");
        }

        AllyProduct offer = new AllyProduct();
        offer.setAlly(ally);
        offer.setProduct(product);
        offer.setPriceAmount(req.priceAmount());
        // Reference price is always entered in USD today (mirrors V91 backfill).
        if (req.priceAmount() != null) {
            offer.setPriceCurrency(currencyRepository.findByCode("USD")
                    .orElseThrow(() -> new IllegalStateException("currency.usd_not_seeded")));
        }
        offer.setDiscountPct(req.discountPct());
        offer.setExclusions(req.exclusions() == null || req.exclusions().isBlank() ? null : req.exclusions().trim());
        // reviewStatus stays at PROPOSED (V11 column default). reviewedBy /
        // reviewedAt remain null until an admin moves it to APPROVED /
        // REJECTED via the workflow endpoints.

        return mapper.toAllyProductDto(offerRepository.save(offer), publicBaseUrl);
    }
}
