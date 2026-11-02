package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import com.fenixcore.optibienestar360.modules.catalog.repository.ProfessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the sub-specialty rule of {@link ProfessionService} (V213 /
 * hub ADR 0031 §6): a parent must be top-level, a profession with children
 * cannot become a child, and nothing is its own parent.
 */
@ExtendWith(MockitoExtension.class)
class ProfessionServiceParentTest {

    @Mock private ProfessionRepository repository;
    @Mock private AllyRepository allyRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private ProfessionService sut() {
        return new ProfessionService(repository, allyRepository, defaultSortResolver);
    }

    @Test
    void noParent_isTopLevel() {
        assertThat(sut().resolveParent(profession(1L, null), null)).isNull();
    }

    @Test
    void topLevelParent_isAccepted() {
        Profession parent = profession(1L, null);
        Profession child = profession(2L, null);
        when(repository.findByUuid(parent.getUuid())).thenReturn(Optional.of(parent));
        when(repository.existsByParent_Id(2L)).thenReturn(false);

        assertThat(sut().resolveParent(child, parent.getUuid())).isSameAs(parent);
    }

    @Test
    void newProfession_withTopLevelParent_isAccepted() {
        Profession parent = profession(1L, null);
        Profession created = new Profession();
        when(repository.findByUuid(parent.getUuid())).thenReturn(Optional.of(parent));

        assertThat(sut().resolveParent(created, parent.getUuid())).isSameAs(parent);
    }

    @Test
    void parentThatIsASubSpecialty_isRejected() {
        Profession grandParent = profession(1L, null);
        Profession parent = profession(2L, grandParent);
        when(repository.findByUuid(parent.getUuid())).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> sut().resolveParent(profession(3L, null), parent.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("profession.parent.too_deep");
    }

    @Test
    void professionWithSubSpecialties_cannotBecomeOne() {
        Profession parent = profession(1L, null);
        when(repository.findByUuid(parent.getUuid())).thenReturn(Optional.of(parent));
        when(repository.existsByParent_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> sut().resolveParent(profession(2L, null), parent.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("profession.parent.has_children");
    }

    @Test
    void ownParent_isRejected() {
        Profession self = profession(1L, null);
        when(repository.findByUuid(self.getUuid())).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> sut().resolveParent(self, self.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("profession.parent.self");
    }

    @Test
    void unknownParent_is404() {
        UUID unknown = UUID.randomUUID();
        when(repository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().resolveParent(profession(1L, null), unknown))
                .isInstanceOf(NoSuchElementException.class);
    }

    private static Profession profession(Long id, Profession parent) {
        Profession p = new Profession();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setParent(parent);
        return p;
    }
}
