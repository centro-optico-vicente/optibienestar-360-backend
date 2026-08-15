package com.fenixcore.optibienestar360.modules.catalog.dto;

/** Usage summary for a catalog entry — how many records reference it via FK. */
public record UsageDto(boolean inUse, long count) {}
