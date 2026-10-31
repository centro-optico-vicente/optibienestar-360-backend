package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyProductUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Partial edits of an offer's preferential rate
 * ({@link AllyProductsAdminService#applyDiscountEdits}): omitted fields keep
 * the stored value, the type is inferred from what is sent, and a lowered
 * price is re-checked against a FLAT amount.
 */
class AllyProductDiscountEditsTest {

    @Test
    void nothingSent_keepsTheStoredDiscount() {
        AllyProduct offer = flatOffer();

        AllyProductsAdminService.applyDiscountEdits(offer, update(null, null, null, null));

        assertThat(offer.getDiscountType()).isEqualTo(DiscountType.FLAT);
        assertThat(offer.getDiscountAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void percentageSent_switchesAFlatOfferToPercentage() {
        AllyProduct offer = flatOffer();

        AllyProductsAdminService.applyDiscountEdits(offer, update(null, new BigDecimal("10"), null, null));

        assertThat(offer.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(offer.getDiscountPct()).isEqualByComparingTo("10");
        assertThat(offer.getDiscountAmount()).isNull();
    }

    @Test
    void amountSent_switchesAPercentageOfferToFlat() {
        AllyProduct offer = new AllyProduct();
        offer.setPriceAmount(new BigDecimal("40.00"));
        offer.applyDiscount(DiscountType.PERCENTAGE, new BigDecimal("10"), null);

        AllyProductsAdminService.applyDiscountEdits(offer, update(null, null, new BigDecimal("8.00"), null));

        assertThat(offer.getDiscountType()).isEqualTo(DiscountType.FLAT);
        assertThat(offer.getFinalPrice()).isEqualByComparingTo("32.00");
    }

    @Test
    void loweredPriceBelowTheFlatAmount_isRejected() {
        AllyProduct offer = flatOffer();
        offer.setPriceAmount(new BigDecimal("4.00"));  // the service applies the new price first

        assertThatThrownBy(() -> AllyProductsAdminService.applyDiscountEdits(offer,
                update(null, null, null, new BigDecimal("4.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_product.discount.exceeds_price");
    }

    private static AllyProduct flatOffer() {
        AllyProduct offer = new AllyProduct();
        offer.setPriceAmount(new BigDecimal("40.00"));
        offer.applyDiscount(DiscountType.FLAT, null, new BigDecimal("5.00"));
        return offer;
    }

    private static AllyProductUpdateRequest update(DiscountType type, BigDecimal pct, BigDecimal amount, BigDecimal price) {
        return new AllyProductUpdateRequest(null, null, null, null, null, price, pct, type, amount, null, null, null);
    }
}
