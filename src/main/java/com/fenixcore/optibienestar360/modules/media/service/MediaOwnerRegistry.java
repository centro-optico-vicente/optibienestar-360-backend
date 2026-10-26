package com.fenixcore.optibienestar360.modules.media.service;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Every {@link MediaOwner} in the application, by key and by table — the Java
 * allowlist of who may own a gallery (the {@code owner_table} column has no
 * CHECK on purpose, so new owners need no migration).
 */
@Component
public class MediaOwnerRegistry {

    private final Map<String, MediaOwner> byKey;
    private final Map<String, MediaOwner> byTable;

    public MediaOwnerRegistry(List<MediaOwner> owners) {
        this.byKey = owners.stream().collect(Collectors.toUnmodifiableMap(MediaOwner::key, Function.identity()));
        this.byTable = owners.stream().collect(Collectors.toUnmodifiableMap(MediaOwner::ownerTable, Function.identity()));
    }

    public MediaOwner byKey(String key) {
        MediaOwner owner = byKey.get(key);
        if (owner == null) {
            throw new NoSuchElementException("media.owner.not_found");
        }
        return owner;
    }

    /** {@code null} for a table nobody registered (rows of a removed owner are skipped, not fatal). */
    public MediaOwner byTableOrNull(String ownerTable) {
        return byTable.get(ownerTable);
    }

    public Collection<MediaOwner> all() {
        return byKey.values();
    }
}
