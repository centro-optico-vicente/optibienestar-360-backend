package com.fenixcore.optibienestar360.modules.promoter.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response for both {@code POST /v1/admin/commissions/approve} and {@code
 * .../reject} (V107, hub plan §4, PR5). {@code cascadedOverridesVoided} is
 * always {@code 0} for an approval (approving never cascades) and reflects
 * how many {@code promoter_hierarchy_overrides} rows were voided in the
 * rejection's dependency chain for a rejection.
 */
public record CommissionApprovalActionResponse(
        List<UUID> commissionUuids,
        int cascadedOverridesVoided
) {}
