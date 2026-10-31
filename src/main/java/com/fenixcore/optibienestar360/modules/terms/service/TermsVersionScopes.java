package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scope rules of ALIADO contract templates (V183), stateless so both
 * {@code TermsVersionService} and {@code AllyAgreementsService} share them
 * (and plain unit tests can call them).
 *
 * <p>A template applies to an ally when every scope field it sets matches:
 * its {@code ally} is that ally, its {@code allyType} is one of the ally's
 * types, its {@code segment} is the ally's own segment ({@code allies.segment},
 * V211 / hub ADR 0031). A template with no scope applies to every ally.</p>
 */
public final class TermsVersionScopes {

    /** Most specific first: a per-ally template, then per ally type, then per segment, then general. */
    public static final Comparator<TermsVersion> MOST_SPECIFIC_FIRST =
            Comparator.comparingInt(TermsVersionScopes::specificity).reversed()
                    .thenComparing(TermsVersion::getValidFrom, Comparator.reverseOrder());

    private TermsVersionScopes() {
    }

    /** Whether {@code version} is an ALIADO template {@code ally} may sign. */
    public static boolean isApplicableTo(TermsVersion version, Ally ally) {
        if (version.getTermType() != TermType.ALLY) return false;
        if (version.getAlly() != null && !Objects.equals(version.getAlly().getId(), ally.getId())) {
            return false;
        }
        Set<AllyType> types = ally.getAllyTypes();
        if (version.getAllyType() != null) {
            Long typeId = version.getAllyType().getId();
            if (types.stream().noneMatch(t -> Objects.equals(t.getId(), typeId))) return false;
        }
        if (version.getSegment() != null && version.getSegment() != ally.getSegment()) {
            return false;
        }
        return true;
    }

    /** Ranking weight for {@link #MOST_SPECIFIC_FIRST}: ally 4, ally type 2, segment 1. */
    public static int specificity(TermsVersion version) {
        return (version.getAlly() != null ? 4 : 0)
                + (version.getAllyType() != null ? 2 : 0)
                + (version.getSegment() != null ? 1 : 0);
    }

    /**
     * The current version of every stream in {@code versions}: per distinct
     * scope combination, the one with the greatest {@code validFrom <= at}.
     * Input order does not matter.
     */
    public static List<TermsVersion> currentPerStream(List<TermsVersion> versions, Instant at) {
        List<TermsVersion> sorted = versions.stream()
                .filter(v -> !v.getValidFrom().isAfter(at))
                .sorted(Comparator.comparing(TermsVersion::getValidFrom).reversed())
                .toList();
        List<TermsVersion> current = new ArrayList<>();
        for (TermsVersion v : sorted) {
            if (current.stream().noneMatch(c -> c.sameStreamAs(v))) current.add(v);
        }
        return current;
    }

    /**
     * The current ALIADO templates {@code ally} may sign, most specific
     * first — what the agreement form offers for that ally.
     */
    public static List<TermsVersion> currentApplicableTo(List<TermsVersion> aliadoVersions, Ally ally, Instant at) {
        return currentPerStream(aliadoVersions, at).stream()
                .filter(v -> isApplicableTo(v, ally))
                .sorted(MOST_SPECIFIC_FIRST)
                .toList();
    }
}
