package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;

/**
 * Agreement file number of an ally, "[CÓDIGO DE ASESOR]-[Nº DE ALIADO]" (V179).
 * Shared by {@code AllyMapper} (ally detail) and the T&C renderer
 * ({@code {{ally.fileNumber}}}), so both always print the same value.
 */
public final class AllyFileNumbers {

    /** Zero-padding width of the "Nº DE ALIADO" half of the file number. */
    public static final int PAD = 4;

    private AllyFileNumbers() {
    }

    /**
     * The capturing advisor's referral code + {@code "-"} + the ally number
     * zero-padded to {@link #PAD} digits ({@code "ABC123-0007"}). Without a
     * capturing advisor (or one without a referral code) it is just the padded
     * number ({@code "0007"}); {@code null} while the ally has no number yet
     * (not persisted).
     */
    public static String of(Ally ally) {
        if (ally == null || ally.getAllyNumber() == null) return null;
        String number = String.format("%0" + PAD + "d", ally.getAllyNumber());
        Promoter promoter = ally.getCapturedByPromoter();
        String advisorCode = promoter == null ? null : promoter.getReferralCode();
        return advisorCode == null || advisorCode.isBlank() ? number : advisorCode + "-" + number;
    }
}
