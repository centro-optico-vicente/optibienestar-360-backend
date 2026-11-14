package com.fenixcore.optibienestar360.modules.member.dto;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of a portfolio distribution, the same for the preview and the real run (ADR LOC-0016).
 * {@code total} = members the split covered (the whole selection in {@code EQUAL}, the sum of the
 * counts in {@code COUNT}); {@code skipped} = those already on the advisor they were dealt to (left
 * untouched); each target's {@code count} = members actually moved to it. So
 * {@code total = skipped + sum(targets.count)}.
 */
public record MemberDistributionResultDto(int total, int skipped, List<TargetResult> targets) {

    public record TargetResult(UUID promoterUuid, String promoterDisplay, int count) {}
}
