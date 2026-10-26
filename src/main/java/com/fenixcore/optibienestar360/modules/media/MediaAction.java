package com.fenixcore.optibienestar360.modules.media;

/** What a caller wants to do with an owner's gallery; each maps to one granular permission per owner. */
public enum MediaAction {
    /** List the gallery. */
    READ,
    /** Upload a file or add an external URL. */
    UPLOAD,
    /** Edit caption, primary image or order. */
    UPDATE,
    DELETE
}
