package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyProfileDto;
import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyProfileUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyProduct;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyOpeningHourRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The ally's own profile in the portal (hub ADR 0036): what a self-registered
 * ally completes while its request is reviewed, and what an approved ally
 * keeps up to date. Membership based like the rest of {@code /v1/me/allies}:
 * any active member reads it, only the OWNER writes. An unknown ally answers
 * the same 403 as a foreign one, so the portal never reveals which allies
 * exist.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyAllyProfileService {

    private final AllyUserRepository allyUserRepository;
    private final AllyOpeningHourRepository openingHourRepository;
    private final AllyProductRepository allyProductRepository;
    private final AlliesService alliesService;

    public MyAllyProfileDto get(UUID allyUuid, UUID actorUserUuid) {
        return toDto(requireMembership(allyUuid, actorUserUuid, false).getAlly());
    }

    @Transactional
    @Auditable(entity = "ally", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public MyAllyProfileDto update(UUID allyUuid, UUID actorUserUuid, MyAllyProfileUpdateRequest req) {
        Ally ally = requireMembership(allyUuid, actorUserUuid, true).getAlly();
        if (ally.getRegistrationStatus() == Ally.RegistrationStatus.REJECTED) {
            throw new IllegalArgumentException("ally.profile.rejected");
        }
        if (req.name() != null) {
            if (ally.getRegistrationStatus() != Ally.RegistrationStatus.PENDING) {
                throw new IllegalArgumentException("ally.profile.name_locked");
            }
            if (req.name().isBlank()) {
                throw new IllegalArgumentException("ally.profile.name_required");
            }
            ally.setName(req.name().trim());
        }

        AllySegments.SiteFields site = new AllySegments.SiteFields(null, req.hostName(), req.officeNumber(),
                req.appointmentPhone(), req.assistantName(), req.commercialZone(), req.tradeName(),
                req.foundedYear(), req.cashRegistersCount(), req.branchesCount(), req.hasPos(),
                req.representativePosition());
        AllySegments.rejectForeignFields(ally.getSegment(), site);
        alliesService.applySiteFields(ally, site, ally.getId());

        if (req.email() != null)         ally.setEmail(blankToNull(req.email()));
        if (req.phone() != null)         ally.setPhone(blankToNull(req.phone()));
        if (req.whatsapp() != null)      ally.setWhatsapp(blankToNull(req.whatsapp()));
        if (req.website() != null)       ally.setWebsite(blankToNull(req.website()));
        if (req.instagram() != null)     ally.setInstagram(blankToNull(req.instagram()));
        if (req.facebook() != null)      ally.setFacebook(blankToNull(req.facebook()));
        if (req.address() != null)       ally.setAddress(blankToNull(req.address()));
        if (req.cityUuid() != null)      ally.setCity(alliesService.resolveCityOptional(req.cityUuid()));
        if (req.googleMapsUrl() != null) ally.setGoogleMapsUrl(blankToNull(req.googleMapsUrl()));
        if (req.description() != null)   ally.setDescription(blankToNull(req.description()));
        return toDto(ally);  // managed → dirty-check on commit
    }

    // ─── Mapping ────────────────────────────────────────────────────────────

    private MyAllyProfileDto toDto(Ally ally) {
        boolean hasHours = openingHourRepository.existsByAllyId(ally.getId());
        boolean hasApprovedOffers = allyProductRepository.existsByAllyIdAndReviewStatusAndActiveTrue(
                ally.getId(), AllyProduct.ReviewStatus.APPROVED);
        City city = ally.getCity();
        List<String> typeNames = ally.getAllyTypes().stream()
                .map(AllyType::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new MyAllyProfileDto(
                ally.getUuid(), ally.getName(),
                ally.getRegistrationStatus() == Ally.RegistrationStatus.PENDING,
                ally.getSegment(), ally.getRegistrationStatus(), ally.getRejectionReason(), typeNames,
                ally.getEmail(), ally.getPhone(), ally.getWhatsapp(), ally.getWebsite(),
                ally.getInstagram(), ally.getFacebook(), ally.getAddress(),
                DisplayRefs.ref(city), city != null ? DisplayRefs.ref(city.getState()) : null,
                ally.getGoogleMapsUrl(), ally.getDescription(),
                ally.getTradeName(), ally.getCommercialZone(), ally.getFoundedYear(),
                ally.getCashRegistersCount(), ally.getBranchesCount(), ally.getHasPos(),
                ally.getRepresentativePosition(),
                ally.getHostName(), ally.getOfficeNumber(), ally.getAppointmentPhone(), ally.getAssistantName(),
                ally.getLogoUrl(), hasHours, hasApprovedOffers,
                completeness(ally, hasHours));
    }

    /** Recommended items for a useful public profile; the portal shows what is missing. */
    static MyAllyProfileDto.Completeness completeness(Ally ally, boolean hasOpeningHours) {
        List<String> missing = new ArrayList<>();
        int total = 0;
        total++; if (isBlank(ally.getLogoUrl()))      missing.add("logo");
        total++; if (isBlank(ally.getDescription()))  missing.add("description");
        total++; if (isBlank(ally.getAddress()))      missing.add("address");
        total++; if (ally.getCity() == null)          missing.add("city");
        total++; if (isBlank(ally.getGoogleMapsUrl())) missing.add("googleMapsUrl");
        total++; if (isBlank(ally.getPhone()) && isBlank(ally.getWhatsapp())) missing.add("contactPhone");
        total++; if (!hasOpeningHours)                missing.add("openingHours");
        total++; if (isBlank(ally.getInstagram()) && isBlank(ally.getFacebook()) && isBlank(ally.getWebsite())) {
            missing.add("socialMedia");
        }
        if (ally.getSegment() == Segment.B2P) {
            total++; if (ally.getHostAlly() == null && isBlank(ally.getHostName()) && isBlank(ally.getOfficeNumber())) {
                missing.add("practiceSite");
            }
            total++; if (isBlank(ally.getAppointmentPhone())) missing.add("appointmentPhone");
        }
        int percent = (int) Math.round(100.0 * (total - missing.size()) / total);
        return new MyAllyProfileDto.Completeness(percent, List.copyOf(missing));
    }

    /** Active membership of the caller in the ally; {@code ownerOnly} for writes. */
    private AllyUser requireMembership(UUID allyUuid, UUID actorUserUuid, boolean ownerOnly) {
        AllyUser membership = actorUserUuid == null ? null
                : allyUserRepository.findActiveByAllyUuidAndUserUuid(allyUuid, actorUserUuid).orElse(null);
        if (membership == null || (ownerOnly && membership.getAllyRole() != AllyRole.OWNER)) {
            throw new AccessDeniedException("ally.profile.not_allowed");
        }
        return membership;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
