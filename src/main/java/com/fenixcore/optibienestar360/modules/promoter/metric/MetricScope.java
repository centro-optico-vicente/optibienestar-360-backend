package com.fenixcore.optibienestar360.modules.promoter.metric;

import java.util.Set;

/**
 * The pool of promoters a rule's providers must aggregate over: which promoter types and ranks
 * it applies to (empty = "all"), and whether system promoters (the house account) count at all.
 */
public record MetricScope(Set<Long> promoterTypeIds, Set<Long> rankIds, boolean includeSystemPromoters) {

    public MetricScope {
        promoterTypeIds = promoterTypeIds == null ? Set.of() : Set.copyOf(promoterTypeIds);
        rankIds = rankIds == null ? Set.of() : Set.copyOf(rankIds);
    }
}
