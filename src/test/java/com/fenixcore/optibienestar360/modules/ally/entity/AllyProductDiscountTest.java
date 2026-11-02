package com.fenixcore.optibienestar360.modules.ally.entity;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the FLAT / PERCENTAGE preferential rate of an offer (V210,
 * hub ADR 0031 §8): the derived final price, the equivalent percentage and
 * the validation that mirrors {@code chk_ally_products_discount_coherence}.
 */
class AllyProductDiscountTest {

    @Test
    void percentage_finalPriceAppliesThePercentage() {
        AllyProduct offer = priced("40.00");
        offer.applyDiscount(DiscountType.PERCENTAGE, new BigDecimal("15"), null);

        assertThat(offer.getFinalPrice()).isEqualByComparingTo("34.00");
        assertThat(offer.getEffectiveDiscountPct()).isEqualByComparingTo("15");
        assertThat(offer.getDiscountAmount()).isNull();
    }

    @Test
    void nullType_meansPercentage() {
        AllyProduct offer = priced("40.00");
        offer.applyDiscount(null, new BigDecimal("10"), new BigDecimal("3"));

        assertThat(offer.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(offer.getDiscountAmount()).isNull();
        assertThat(offer.getFinalPrice()).isEqualByComparingTo("36.00");
    }

    @Test
    void percentage_withoutDiscount_isTheFullPrice() {
        AllyProduct offer = priced("40.00");
        offer.applyDiscount(DiscountType.PERCENTAGE, null, null);

        assertThat(offer.getFinalPrice()).isEqualByComparingTo("40.00");
        assertThat(offer.getEffectiveDiscountPct()).isNull();
    }

    @Test
    void flat_finalPriceSubtractsTheAmountAndClearsThePercentage() {
        AllyProduct offer = priced("40.00");
        offer.setDiscountPct(new BigDecimal("20"));
        offer.applyDiscount(DiscountType.FLAT, new BigDecimal("20"), new BigDecimal("5.00"));

        assertThat(offer.getFinalPrice()).isEqualByComparingTo("35.00");
        assertThat(offer.getDiscountPct()).isNull();
        assertThat(offer.getEffectiveDiscountPct()).isEqualByComparingTo("12.50");
    }

    @Test
    void flat_equalToThePrice_isAllowed() {
        AllyProduct offer = priced("40.00");
        offer.applyDiscount(DiscountType.FLAT, null, new BigDecimal("40.00"));

        assertThat(offer.getFinalPrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void flat_aboveThePrice_isRejected() {
        AllyProduct offer = priced("40.00");

        assertThatThrownBy(() -> offer.applyDiscount(DiscountType.FLAT, null, new BigDecimal("40.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_product.discount.exceeds_price");
    }

    @Test
    void flat_withoutPrice_isRejected() {
        AllyProduct offer = new AllyProduct();

        assertThatThrownBy(() -> offer.applyDiscount(DiscountType.FLAT, null, new BigDecimal("5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_product.discount.flat_requires_price");
    }

    @Test
    void flat_withoutAmount_isRejected() {
        AllyProduct offer = priced("40.00");

        assertThatThrownBy(() -> offer.applyDiscount(DiscountType.FLAT, new BigDecimal("10"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_product.discount.flat_requires_amount");
        assertThatThrownBy(() -> offer.applyDiscount(DiscountType.FLAT, null, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_product.discount.flat_requires_amount");
    }

    @Test
    void noPrice_hasNoFinalPrice() {
        AllyProduct offer = new AllyProduct();
        offer.applyDiscount(DiscountType.PERCENTAGE, new BigDecimal("10"), null);

        assertThat(offer.getFinalPrice()).isNull();
    }

    private static AllyProduct priced(String price) {
        AllyProduct offer = new AllyProduct();
        offer.setPriceAmount(new BigDecimal(price));
        return offer;
    }
}
