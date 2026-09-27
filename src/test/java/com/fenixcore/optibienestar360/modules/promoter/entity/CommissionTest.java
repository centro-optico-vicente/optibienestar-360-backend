package com.fenixcore.optibienestar360.modules.promoter.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers {@link Commission#getRuleSource()} — the derived rule-source discriminator. */
class CommissionTest {

    @Test
    void getRuleSource_returnsTier_whenOnlyCommissionTierIdSet() {
        Commission commission = new Commission();
        commission.setCommissionTierId(1L);

        assertThat(commission.getRuleSource()).isEqualTo(Commission.RuleSource.TIER);
    }

    @Test
    void getRuleSource_returnsCollectionTier_whenOnlyCollectionTierIdSet() {
        Commission commission = new Commission();
        commission.setCollectionTierId(1L);

        assertThat(commission.getRuleSource()).isEqualTo(Commission.RuleSource.COLLECTION_TIER);
    }

    @Test
    void getRuleSource_prefersCollectionTier_whenBothSet() {
        Commission commission = new Commission();
        commission.setCommissionTierId(1L);
        commission.setCollectionTierId(2L);

        assertThat(commission.getRuleSource()).isEqualTo(Commission.RuleSource.COLLECTION_TIER);
    }

    @Test
    void getRuleSource_returnsNull_whenNeitherSet() {
        Commission commission = new Commission();

        assertThat(commission.getRuleSource()).isNull();
    }
}
