package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The advisor of an ally ("who brought it in", V179): assigning, changing and clearing it, and the
 * advisor's own read-only list.
 */
@ExtendWith(MockitoExtension.class)
class AlliesServiceAdvisorTest {

    @Mock private AllyRepository repository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private AllyMapper mapper;

    @InjectMocks
    private AlliesService service;

    private Ally ally() {
        Ally ally = new Ally();
        ally.setUuid(UUID.randomUUID());
        ally.setName("Optica Sample");
        return ally;
    }

    private Promoter promoter() {
        Promoter promoter = new Promoter();
        promoter.setUuid(UUID.randomUUID());
        promoter.setDisplayName("Ana Asesora");
        return promoter;
    }

    @Test
    void assignAdvisor_setsTheAdvisor_andReturnsTheDetail() {
        Ally ally = ally();
        Promoter advisor = promoter();
        AllyDetailDto detail = org.mockito.Mockito.mock(AllyDetailDto.class);
        when(repository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(promoterRepository.findByUuid(advisor.getUuid())).thenReturn(Optional.of(advisor));
        when(mapper.toDetail(ally)).thenReturn(detail);

        AllyDetailDto result = service.assignAdvisor(ally.getUuid(), advisor.getUuid());

        assertThat(ally.getCapturedByPromoter()).isSameAs(advisor);
        assertThat(result).isSameAs(detail);
    }

    @Test
    void assignAdvisor_withNull_clearsTheAdvisor() {
        Ally ally = ally();
        ally.setCapturedByPromoter(promoter());
        when(repository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        service.assignAdvisor(ally.getUuid(), null);

        assertThat(ally.getCapturedByPromoter()).isNull();
    }

    @Test
    void assignAdvisor_withAnUnknownAdvisor_is404_andKeepsTheCurrentOne() {
        Ally ally = ally();
        Promoter current = promoter();
        ally.setCapturedByPromoter(current);
        UUID unknown = UUID.randomUUID();
        when(repository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(promoterRepository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignAdvisor(ally.getUuid(), unknown))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("promoter.not_found");
        assertThat(ally.getCapturedByPromoter()).isSameAs(current);
    }

    @Test
    void assignAdvisor_withAnUnknownAlly_is404() {
        UUID unknown = UUID.randomUUID();
        when(repository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignAdvisor(unknown, UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ally.not_found");
    }

    @Test
    void listCapturedBy_whenTheCallerIsNotAnAdvisor_is404() {
        UUID userUuid = UUID.randomUUID();
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listCapturedBy(userUuid, null, PageRequest.of(0, 20)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
    }
}
