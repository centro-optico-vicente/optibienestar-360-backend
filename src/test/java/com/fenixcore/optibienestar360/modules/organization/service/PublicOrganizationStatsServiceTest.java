package com.fenixcore.optibienestar360.modules.organization.service;

import com.fenixcore.optibienestar360.modules.ally.service.AlliesService;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.dashboard.repository.DashboardQueries;
import com.fenixcore.optibienestar360.modules.organization.dto.PublicOrganizationStatsDto;
import com.fenixcore.optibienestar360.modules.organization.entity.AlliesCounterMode;
import com.fenixcore.optibienestar360.modules.organization.entity.MembersCounterMode;
import com.fenixcore.optibienestar360.modules.organization.entity.Organization;
import com.fenixcore.optibienestar360.modules.organization.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicOrganizationStatsService} (hub ADR 0042): the
 * counter modes (visibility and ally segment) and the server-side minimum of
 * the login counters.
 */
@ExtendWith(MockitoExtension.class)
class PublicOrganizationStatsServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private AlliesService alliesService;
    @Mock private DashboardQueries dashboardQueries;

    private PublicOrganizationStatsService service() {
        return new PublicOrganizationStatsService(organizationRepository, alliesService, dashboardQueries);
    }

    private static Organization organization(AlliesCounterMode alliesMode, int alliesMin,
                                             MembersCounterMode membersMode, int membersMin) {
        Organization org = new Organization();
        org.setAlliesCounterMode(alliesMode);
        org.setAlliesCounterMin(alliesMin);
        org.setMembersCounterMode(membersMode);
        org.setMembersCounterMin(membersMin);
        return org;
    }

    @Test
    void hiddenCountersReturnNoFiguresAndRunNoQuery() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(AlliesCounterMode.HIDDEN, 20, MembersCounterMode.HIDDEN, 20));

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().visible()).isFalse();
        assertThat(stats.allies().count()).isNull();
        assertThat(stats.allies().mode()).isNull();
        assertThat(stats.members().visible()).isFalse();
        assertThat(stats.members().count()).isNull();
        assertThat(stats.members().mode()).isNull();
        verifyNoInteractions(alliesService, dashboardQueries);
    }

    @Test
    void professionalModeCountsOnlyB2pAllies() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(AlliesCounterMode.PROFESSIONAL, 0, MembersCounterMode.HIDDEN, 20));
        when(alliesService.countPubliclyVisible(Segment.B2P)).thenReturn(8L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().visible()).isTrue();
        assertThat(stats.allies().count()).isEqualTo(8L);
        assertThat(stats.allies().mode()).isEqualTo(AlliesCounterMode.PROFESSIONAL);
    }

    @Test
    void commercialModeCountsOnlyB2bAllies() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(AlliesCounterMode.COMMERCIAL, 0, MembersCounterMode.HIDDEN, 20));
        when(alliesService.countPubliclyVisible(Segment.B2B)).thenReturn(12L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().count()).isEqualTo(12L);
        assertThat(stats.allies().mode()).isEqualTo(AlliesCounterMode.COMMERCIAL);
    }

    @Test
    void commercialAndProfessionalModeCountsEverySegment() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(AlliesCounterMode.COMMERCIAL_AND_PROFESSIONAL, 0, MembersCounterMode.HIDDEN, 20));
        when(alliesService.countPubliclyVisible(isNull())).thenReturn(20L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().count()).isEqualTo(20L);
        assertThat(stats.allies().mode()).isEqualTo(AlliesCounterMode.COMMERCIAL_AND_PROFESSIONAL);
    }

    @Test
    void countBelowTheMinimumIsWithheld() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(AlliesCounterMode.COMMERCIAL_AND_PROFESSIONAL, 20, MembersCounterMode.MEMBERS, 20));
        when(alliesService.countPubliclyVisible(isNull())).thenReturn(19L);
        when(dashboardQueries.countMembersWithActiveMembership()).thenReturn(3L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().visible()).isTrue();
        assertThat(stats.allies().count()).isNull();
        assertThat(stats.allies().mode()).isEqualTo(AlliesCounterMode.COMMERCIAL_AND_PROFESSIONAL);
        assertThat(stats.members().visible()).isTrue();
        assertThat(stats.members().count()).isNull();
        assertThat(stats.members().mode()).isEqualTo(MembersCounterMode.MEMBERS);
    }

    @Test
    void countAtTheMinimumIsShown() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(AlliesCounterMode.PROFESSIONAL, 20, MembersCounterMode.MEMBERS, 20));
        when(alliesService.countPubliclyVisible(Segment.B2P)).thenReturn(20L);
        when(dashboardQueries.countMembersWithActiveMembership()).thenReturn(20L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.allies().count()).isEqualTo(20L);
        assertThat(stats.members().count()).isEqualTo(20L);
        verify(dashboardQueries, never()).countActiveBeneficiariesOfMembersWithActiveMembership();
    }

    @Test
    void beneficiariesModeAddsActiveBeneficiariesBeforeApplyingTheMinimum() {
        when(organizationRepository.findSingleton())
                .thenReturn(organization(AlliesCounterMode.HIDDEN, 20, MembersCounterMode.MEMBERS_AND_BENEFICIARIES, 25));
        when(dashboardQueries.countMembersWithActiveMembership()).thenReturn(15L);
        when(dashboardQueries.countActiveBeneficiariesOfMembersWithActiveMembership()).thenReturn(12L);

        PublicOrganizationStatsDto stats = service().getStats();

        assertThat(stats.members().visible()).isTrue();
        assertThat(stats.members().count()).isEqualTo(27L);
        assertThat(stats.members().mode()).isEqualTo(MembersCounterMode.MEMBERS_AND_BENEFICIARIES);
        verifyNoInteractions(alliesService);
    }
}
