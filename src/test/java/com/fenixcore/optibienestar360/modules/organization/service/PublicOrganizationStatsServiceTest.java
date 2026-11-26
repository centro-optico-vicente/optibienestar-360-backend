package com.fenixcore.optibienestar360.modules.organization.service;

import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationStatsDto;
import com.fenixcore.optibienestar360.modules.organization.entity.MembersCounterMode;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicOrganizationStatsService} (hub ADR 0042): the
 * visibility switch and the server-side minimum of the login counters.
 */
@ExtendWith(MockitoExtension.class)
class PublicOrganizationStatsServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private AlliesService alliesService;
    @Mock private DashboardQueries dashboardQueries;

    private PublicOrganizationStatsService service() {
        return new PublicOrganizationStatsService(organizationRepository, alliesService, dashboardQueries);
    }

    private static Organization organization(boolean alliesVisible, int alliesMin,
                                             MembersCounterMode membersMode, int membersMin) {
        Organization org = new Organization();
        org.setAlliesCounterVisible(alliesVisible);
        org.setAlliesCounterMin(alliesMin);
        org.setMembersCounterMode(membersMode);
        org.setMembersCounterMin(membersMin);
        return org;
    }

    @Test
    void hiddenCountersReturnNoFiguresAndRunNoQuery() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(false, 20, MembersCounterMode.HIDDEN, 20));

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().visible()).isFalse();
        assertThat(stats.allies().count()).isNull();
        assertThat(stats.members().visible()).isFalse();
        assertThat(stats.members().count()).isNull();
        assertThat(stats.members().mode()).isNull();
        verifyNoInteractions(alliesService, dashboardQueries);
    }

    @Test
    void countBelowTheMinimumIsWithheld() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(true, 20, MembersCounterMode.MEMBERS, 20));
        when(alliesService.countPubliclyVisible()).thenReturn(19L);
        when(dashboardQueries.countMembersWithActiveMembership()).thenReturn(3L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().visible()).isTrue();
        assertThat(stats.allies().count()).isNull();
        assertThat(stats.members().visible()).isTrue();
        assertThat(stats.members().count()).isNull();
        assertThat(stats.members().mode()).isEqualTo(MembersCounterMode.MEMBERS);
    }

    @Test
    void countAtTheMinimumIsShown() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(true, 20, MembersCounterMode.MEMBERS, 20));
        when(alliesService.countPubliclyVisible()).thenReturn(20L);
        when(dashboardQueries.countMembersWithActiveMembership()).thenReturn(20L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().count()).isEqualTo(20L);
        assertThat(stats.members().count()).isEqualTo(20L);
        verify(dashboardQueries, never()).countActiveBeneficiariesOfMembersWithActiveMembership();
    }

    @Test
    void zeroMinimumAlwaysShowsTheFigure() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(true, 0, MembersCounterMode.HIDDEN, 20));
        when(alliesService.countPubliclyVisible()).thenReturn(0L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().visible()).isTrue();
        assertThat(stats.allies().count()).isZero();
    }

    @Test
    void beneficiariesModeAddsActiveBeneficiariesBeforeApplyingTheMinimum() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(false, 20, MembersCounterMode.MEMBERS_AND_BENEFICIARIES, 25));
        when(dashboardQueries.countMembersWithActiveMembership()).thenReturn(15L);
        when(dashboardQueries.countActiveBeneficiariesOfMembersWithActiveMembership()).thenReturn(12L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.members().visible()).isTrue();
        assertThat(stats.members().count()).isEqualTo(27L);
        assertThat(stats.members().mode()).isEqualTo(MembersCounterMode.MEMBERS_AND_BENEFICIARIES);
        verifyNoInteractions(alliesService);
    }
}
