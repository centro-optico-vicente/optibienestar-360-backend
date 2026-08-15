package com.fenixcore.optibienestar360.common.storage.dto;

/**
 * Outcome of {@code AttachedFileService.publish(...)} — exactly one of the
 * two fields is set, depending on whether the caller asked for a permanent
 * publish (copy to the public bucket) or a temporary one (download link).
 */
public record PublishResultDto(String publicKey, DownloadLinkDto downloadLink) {

    public static PublishResultDto permanent(String publicKey) {
        return new PublishResultDto(publicKey, null);
    }

    public static PublishResultDto temporary(DownloadLinkDto downloadLink) {
        return new PublishResultDto(null, downloadLink);
    }
}
