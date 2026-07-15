package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyDto;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ally-side self-identification. Backs {@code GET /v1/me/allies}.
 *
 * <p>Split from {@link AllyUsersService} (admin-side management of the same
 * pivot) for the same reason {@link AllyServicesProposeService} is split from
 * {@link AllyServicesAdminService}: the admin surface is scoped by a parent
 * ally UUID taken from the path, this one is scoped by the JWT subject and
 * can never read another user's memberships.</p>
 *
 * <p>Without this the partner portal has no way to learn its own
 * {@code allyUuid}, which is a required field of
 * {@code POST /v1/ally/benefit-usage} and {@code POST /v1/aliado/services} —
 * the operator would have to supply it by hand.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyAlliesService {

    private final AllyUserRepository allyUserRepository;
    private final AllyMapper mapper;

    /**
     * The allies the given user actively operates on, primary membership
     * first and alphabetical within the rest — the portal preselects the head
     * of this list, so the order is part of the contract, not cosmetic.
     *
     * <p>Returns an empty list rather than 404 when the caller holds
     * {@code ALLY_VIEW_OWN} but has no membership yet: a freshly created
     * partner user not yet attached to an ally is a legitimate state, and the
     * portal renders an empty-state for it. This is a collection endpoint, so
     * "none" is an empty array — a 404 is reserved for lookups by id.</p>
     *
     * @param userUuid the JWT subject.
     */
    public List<MyAllyDto> listMyAllies(UUID userUuid) {
        return allyUserRepository.findActiveByUserUuid(userUuid).stream()
                .sorted(Comparator.comparing(AllyUser::isPrimary).reversed()
                        .thenComparing(au -> au.getAlly().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(mapper::toMyAllyDto)
                .toList();
    }
}
