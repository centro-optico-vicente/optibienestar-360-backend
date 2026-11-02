package com.fenixcore.optibienestar360.modules.ally.repository;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyOpeningHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AllyOpeningHourRepository extends JpaRepository<AllyOpeningHour, Long> {

    /** The ally's week in display order (day, then opening time). */
    @Query("SELECT h FROM AllyOpeningHour h WHERE h.ally.id = :allyId ORDER BY h.dayOfWeek, h.opensAt")
    List<AllyOpeningHour> findWeek(@Param("allyId") Long allyId);

    /** Drops the whole week before it is replaced (rows are not soft-deleted). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AllyOpeningHour h WHERE h.ally.id = :allyId")
    int deleteWeek(@Param("allyId") Long allyId);
}
