package com.fenixcore.optibienestar360.common.storage.dto;

import java.time.Instant;

/** Response for creating a shareable {@code file_download_links} row — the URL that gets emailed/shown. */
public record DownloadLinkDto(String url, Instant expiresAt) {}
