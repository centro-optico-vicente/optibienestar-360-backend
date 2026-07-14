package com.fenixcore.optibienestar360.modules.auth.repository;

import com.fenixcore.optibienestar360.modules.auth.entity.UserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(readOnly = true)
public interface UserPasswordHistoryRepository extends JpaRepository<UserPasswordHistory, Long> {

    @Query("SELECT h FROM UserPasswordHistory h WHERE h.user.id = :userId ORDER BY h.createdAt DESC")
    List<UserPasswordHistory> findRecentByUserId(@Param("userId") Long userId);

    // Write query — must override the interface-level readOnly so the DELETE
    // can run. The caller (AuthService.savePasswordHistory) already supplies a
    // write transaction; this annotation is defense-in-depth for any future
    // standalone caller.
    @Transactional
    @Modifying
    @Query(value = """
            DELETE FROM user_password_history
            WHERE user_id = :userId
              AND user_password_history_id NOT IN (
                  SELECT user_password_history_id FROM user_password_history
                  WHERE user_id = :userId
                  ORDER BY created_at DESC
                  LIMIT :keep
              )
            """, nativeQuery = true)
    void pruneOlderThan(@Param("userId") Long userId, @Param("keep") int keep);
}
