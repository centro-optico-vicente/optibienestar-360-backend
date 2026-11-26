package com.fenixcore.optibienestar360.modules.organization.service;

import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationStatsDto;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationStatsDto.AlliesCounter;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationStatsDto.MembersCounter;
import com.fenixcore.optibienestar360.modules.organization.entity.MembersCounterMode;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public login counters (hub ADR 0042, V265): what the allies and members
 * cards of the login screen show, driven by the organization settings.
 *
 * <p>The minimum is enforced here, not in the client: a figure below the
 * configured minimum is returned as {@code null}, so it never reaches the
 * browser. A hidden counter does not run its query at all.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicOrganizationStatsService {

    private final OrganizationRepository organizationRepository;
    private final AlliesService alliesService;
    private final DashboardQueries dashboardQueries;

    public PublicOrganizationStatsDto getStats() {
        Organization org = organizationRepository.findSingleton();
        return new PublicOrganizationStatsDto(alliesCounter(org), membersCounter(org));
    }

    private AlliesCounter alliesCounter(Organization org) {
        if (!org.isAlliesCounterVisible()) {
            return new AlliesCounter(false, null);
        }
        long count = alliesService.countPubliclyVisible();
        return new AlliesCounter(true, aboveMinimum(count, org.getAlliesCounterMin()));
    }

    private MembersCounter membersCounter(Organization org) {
        MembersCounterMode mode = org.getMembersCounterMode();
        if (mode == null || mode == MembersCounterMode.HIDDEN) {
            return new MembersCounter(false, null, null);
        }
        long count = dashboardQueries.countMembersWithActiveMembership();
        if (mode == MembersCounterMode.MEMBERS_AND_BENEFICIARIES) {
            count += dashboardQueries.countActiveBeneficiariesOfMembersWithActiveMembership();
        }
        return new MembersCounter(true, aboveMinimum(count, org.getMembersCounterMin()), mode);
    }

    /** The count when it reaches {@code min}; otherwise {@code null} (the card shows a generic text). */
    static Long aboveMinimum(long count, int min) {
        return count >= min ? count : null;
    }
}
