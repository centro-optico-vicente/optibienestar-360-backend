package com.fenixcore.optibienestar360.modules.promoter.dto;

import java.util.List;
import java.util.UUID;

/**
 * One node of the hierarchy tree ({@code GET
 * /v1/admin/promoters/hierarchy-tree}) — feeds a Nuxt org-chart / collapsible
 * tree view directly, no client-side reassembly of a flat list needed.
 *
 * <p>Editing (move a node under another rank, or pull it out to the top of
 * its own chain) is <b>not</b> a separate endpoint — the client calls the
 * existing {@code POST /v1/admin/promoters/{uuid}/assign-supervisor} with
 * the target's {@code uuid} (drag "into" another node) or {@code
 * supervisorUuid = null} (drag "out", making it a root); this DTO carries
 * {@link #supervisorUuid} precisely so the client always knows the current
 * value to diff against before firing that call.</p>
 */
public record PromoterHierarchyNodeDto(
        UUID uuid,
        String displayName,
        String referralCode,
        String rankCode,
        String rankName,
        UUID supervisorUuid,
        List<PromoterHierarchyNodeDto> children
) {}
