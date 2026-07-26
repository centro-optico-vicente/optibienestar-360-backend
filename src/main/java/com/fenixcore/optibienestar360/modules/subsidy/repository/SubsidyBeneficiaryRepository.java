package com.fenixcore.optibienestar360.modules.subsidy.repository;

import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyBeneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Transactional(readOnly = true)
public interface SubsidyBeneficiaryRepository extends JpaRepository<SubsidyBeneficiary, Long> {

    /**
     * Active exoneration rows for a beneficiary whose parent subsidy is live and
     * in-window on {@code on}. Used by {@code SubsidyResolver} to decide whether
     * a beneficiary's extra inscription is exonerated (e.g. on reactivation).
     */
    @Query("""
            SELECT sb FROM SubsidyBeneficiary sb
            WHERE sb.active = true
              AND sb.beneficiary.id = :beneficiaryId
              AND sb.subsidy.active = true
              AND sb.subsidy.validFrom <= :on
              AND (sb.subsidy.validUntil IS NULL OR sb.subsidy.validUntil >= :on)
            """)
    List<SubsidyBeneficiary> findActiveForBeneficiary(@Param("beneficiaryId") Long beneficiaryId,
                                                      @Param("on") LocalDate on);
}
