package com.fenixcore.optibienestar360.common.storage;

/**
 * Where a stored object lives: its visibility decides the bucket (public vs private) and the key is the R2 object
 * key. Collected before the rows that reference it are deleted, so the object can be removed afterwards.
 */
public record StoredObject(FileVisibility visibility, String key) {
}
