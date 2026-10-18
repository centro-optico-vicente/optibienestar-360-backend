package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterReference;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface PromoterReferenceRepository extends JpaRepository<PromoterReference, Long> {

    Optional<PromoterReference> findByUuid(UUID uuid);

    List<PromoterReference> findByPromoterIdAndActiveTrue(Long promoterId, Sort sort);
}
