package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.promoter.dto.MyReferralDto;
import com.fenixcore.optibienestar360.modules.promoter.mapper.ReferralMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.ReferralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-side service over {@code referrals}. Plural-name convention
 * matches {@code PaymentsService} / {@code CommissionsService} — the
 * singular {@link ReferralService} owns the write engine (register +
 * apply reward) and this one owns read endpoints.
 *
 * <p>v1 exposes only the affiliate's own view ({@link #listForUser}).
 * When an admin queue endpoint lands later, the {@code list(Pageable,
 * filter, q)} method drops in alongside, same RSQL pattern the
 * Commission / Payment services use.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferralsService {

    private final ReferralRepository repository;
    private final ReferralMapper mapper;

    /**
     * Powers {@code GET /v1/me/referrals}. Returns the page of referrals
     * where the JWT-resolved user is the REFERRER. No filter / no
     * free-text — this surface is the full history, not a curated query.
     */
    public Page<MyReferralDto> listForUser(UUID userUuid, Pageable pageable) {
        return repository.findOwnByUserUuid(userUuid, pageable).map(mapper::toMyDto);
    }
}
