package com.fenixcore.optibienestar360.modules.exchangerate.repository;

import com.fenixcore.optibienestar360.modules.catalog.entity.Country;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Transactional(readOnly = true)
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    Optional<Holiday> findByUuid(UUID uuid);

    /**
     * Every active holiday rule applicable at the national level for
     * {@code country} — i.e. GENERAL ({@code country IS NULL}) or NATIONAL
     * ({@code country = :country AND state IS NULL}) rows. Regional/local
     * rows are excluded — they don't apply to a whole-country business-day
     * check. Used by {@code BusinessDayCalculator}.
     */
    @Query("SELECT h FROM Holiday h WHERE h.active = true AND h.state IS NULL AND (h.country IS NULL OR h.country = :country)")
    List<Holiday> findApplicableNationalRules(@Param("country") Country country);
}
