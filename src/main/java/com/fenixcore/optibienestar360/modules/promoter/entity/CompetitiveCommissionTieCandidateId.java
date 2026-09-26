package com.fenixcore.optibienestar360.modules.promoter.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link CompetitiveCommissionTieCandidate} — one row per (tie, promoter). */
public class CompetitiveCommissionTieCandidateId implements Serializable {

    private Long tie;
    private Long promoter;

    public CompetitiveCommissionTieCandidateId() {
    }

    public CompetitiveCommissionTieCandidateId(Long tie, Long promoter) {
        this.tie = tie;
        this.promoter = promoter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompetitiveCommissionTieCandidateId that)) return false;
        return Objects.equals(tie, that.tie) && Objects.equals(promoter, that.promoter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tie, promoter);
    }
}
