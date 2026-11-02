package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;

/**
 * Agreement file number of an ally, as each agreement form prints it (hub ADR
 * 0031 §10): the B2B "Convenio de Integración Comercial" uses just
 * "[Nº DE ALIADO]", the B2P "Convenio de Integración Profesional" uses
 * "[CÓDIGO DE ASESOR]-[Nº DE ALIADO]". Shared by {@code AllyMapper} (ally
 * detail) and the T&C renderer ({@code {{ally.fileNumber}}}), so both always
 * print the same value.
 */
public final class AllyFileNumbers {

    /** Zero-padding width of the "Nº DE ALIADO" half of the file number. */
    public static final int PAD = 4;

    private AllyFileNumbers() {
    }

    /**
     * B2B: the ally number zero-padded to {@link #PAD} digits ({@code "0007"}).
     * B2P: the capturing advisor's referral code + {@code "-"} + that number
     * ({@code "ABC123-0007"}), or just the number without a capturing advisor
     * (or one without a referral code). {@code null} while the ally has no
     * number yet (not persisted).
     */
    public static String of(Ally ally) {
        if (ally == null || ally.getAllyNumber() == null) return null;
        String number = String.format("%0" + PAD + "d", ally.getAllyNumber());
        if (ally.getSegment() != AllyType.Segment.B2P) return number;
        Promoter promoter = ally.getCapturedByPromoter();
        String advisorCode = promoter == null ? null : promoter.getReferralCode();
        return advisorCode == null || advisorCode.isBlank() ? number : advisorCode + "-" + number;
    }
}
