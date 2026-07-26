package com.fenixcore.optibienestar360.modules.promoter.repository;

import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterMemberContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Insert-only log of a promoter's collection outreach (V36). Writes are appends
 * from {@code PromoterCollectionService}; the only read is the per-affiliate
 * history, which rides the V36 index
 * {@code (promoter_id, member_id, created_at DESC)}.
 */
@Transactional(readOnly = true)
public interface PromoterMemberContactRepository extends JpaRepository<PromoterMemberContact, Long> {

    List<PromoterMemberContact> findByPromoterIdAndMemberIdOrderByCreatedAtDesc(Long promoterId, Long memberId);
}
