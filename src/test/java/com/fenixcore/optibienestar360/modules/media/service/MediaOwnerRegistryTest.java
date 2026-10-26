package com.fenixcore.optibienestar360.modules.media.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The registry is the Java allowlist of gallery owners (hub ADR 0027): unknown keys are 404s, never errors. */
class MediaOwnerRegistryTest {

    private static MediaOwner owner(String key, String table) {
        MediaOwner owner = mock(MediaOwner.class);
        when(owner.key()).thenReturn(key);
        when(owner.ownerTable()).thenReturn(table);
        return owner;
    }

    @Test
    void resolvesByKeyAndByTable() {
        MediaOwner ally = owner("ALLY", "allies");
        MediaOwnerRegistry registry = new MediaOwnerRegistry(List.of(ally, owner("PRODUCT", "products")));

        assertThat(registry.byKey("ALLY")).isSameAs(ally);
        assertThat(registry.byTableOrNull("allies")).isSameAs(ally);
        assertThat(registry.all()).hasSize(2);
    }

    @Test
    void unknownOwner_isNotFound_orNullByTable() {
        MediaOwnerRegistry registry = new MediaOwnerRegistry(List.of(owner("ALLY", "allies")));

        assertThatThrownBy(() -> registry.byKey("MEMBER"))
                .isInstanceOf(NoSuchElementException.class).hasMessage("media.owner.not_found");
        assertThat(registry.byTableOrNull("members")).isNull();
    }
}
