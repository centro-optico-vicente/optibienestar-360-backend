package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoterRankRepository extends JpaRepository<PromoterRank, Long>, JpaSpecificationExecutor<PromoterRank> {

    Optional<PromoterRank> findByUuid(UUID uuid);

    Optional<PromoterRank> findByCode(String code);

    List<PromoterRank> findAllByActiveTrueOrderByHierarchyLevel();

    boolean existsByHierarchyLevel(int hierarchyLevel);
}
