package com.fenixcore.optibienestar360.modules.terms.repository;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface TermsAcceptanceRepository extends JpaRepository<TermsAcceptance, Long> {

    boolean existsByUserIdAndTermsVersionId(Long userId, Long termsVersionId);

    List<TermsAcceptance> findByUserIdAndTermsVersionIdIn(Long userId, List<Long> termsVersionIds);
}
