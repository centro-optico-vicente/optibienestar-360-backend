package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.OpeningHourDto;
import com.fenixcore.optibienestar360.modules.ally.dto.OpeningHoursRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyOpeningHour;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyOpeningHourRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Weekly opening hours of an ally (V214, hub ADR 0031 §7). The week is read
 * and replaced as a whole.
 *
 * <p>Staff use the admin endpoints ({@code ALLY_VIEW_ALL} /
 * {@code ALLY_OPENING_HOURS_UPDATE}). From the ally portal any active member
 * of the ally reads it, and only an active OWNER replaces it (on top of
 * {@code ALLY_OPENING_HOURS_UPDATE_OWN}) — STAFF and VIEWER do not change
 * the ally's public data.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyOpeningHoursService {

    private final AllyRepository allyRepository;
    private final AllyOpeningHourRepository hourRepository;
    private final AllyUserRepository allyUserRepository;

    public List<OpeningHourDto> get(UUID allyUuid) {
        return toDtos(hourRepository.findWeek(findAlly(allyUuid).getId()));
    }

    @Transactional
    public List<OpeningHourDto> replace(UUID allyUuid, OpeningHoursRequest request) {
        return replaceWeek(findAlly(allyUuid), request);
    }

    /** Portal read: any active membership on the ally (VIEWER included). */
    public List<OpeningHourDto> getForMember(UUID allyUuid, UUID actorUserUuid) {
        requireMembership(allyUuid, actorUserUuid, false);
        return get(allyUuid);
    }

    /** Portal write: only an active OWNER of the ally. */
    @Transactional
    public List<OpeningHourDto> replaceForOwner(UUID allyUuid, UUID actorUserUuid, OpeningHoursRequest request) {
        requireMembership(allyUuid, actorUserUuid, true);
        return replace(allyUuid, request);
    }

    /** DTOs of a stored week, in display order, with localized day names. */
    public static List<OpeningHourDto> toDtos(Collection<AllyOpeningHour> hours) {
        Locale locale = LocaleContextHolder.getLocale();
        return OpeningHoursRules.of(hours).stream()
                .sorted(Comparator.comparingInt(OpeningHoursRules.Slot::dayOfWeek)
                        .thenComparing(OpeningHoursRules.Slot::opensAt))
                .map(slot -> new OpeningHourDto(slot.dayOfWeek(), OpeningHoursRules.dayName(slot.dayOfWeek(), locale),
                        slot.opensAt(), slot.closesAt()))
                .toList();
    }

    private List<OpeningHourDto> replaceWeek(Ally ally, OpeningHoursRequest request) {
        List<OpeningHoursRules.Slot> week = request.ranges().stream()
                .map(r -> new OpeningHoursRules.Slot(r.dayOfWeek(), r.opensAt(), r.closesAt()))
                .toList();
        OpeningHoursRules.validate(week);
        hourRepository.deleteWeek(ally.getId());
        List<AllyOpeningHour> rows = week.stream().map(slot -> {
            AllyOpeningHour row = new AllyOpeningHour();
            row.setAlly(ally);
            row.setDayOfWeek((short) slot.dayOfWeek());
            row.setOpensAt(slot.opensAt());
            row.setClosesAt(slot.closesAt());
            return row;
        }).toList();
        return toDtos(hourRepository.saveAll(rows));
    }

    private Ally findAlly(UUID allyUuid) {
        return allyRepository.findByUuid(allyUuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    /** An unknown ally answers the same 403 as a foreign one: the portal never reveals which allies exist. */
    private void requireMembership(UUID allyUuid, UUID actorUserUuid, boolean ownerOnly) {
        AllyUser membership = actorUserUuid == null ? null
                : allyUserRepository.findActiveByAllyUuidAndUserUuid(allyUuid, actorUserUuid).orElse(null);
        if (membership == null || (ownerOnly && membership.getAllyRole() != AllyRole.OWNER)) {
            throw new AccessDeniedException("ally.opening_hours.not_allowed");
        }
    }
}
