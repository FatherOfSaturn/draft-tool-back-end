package org.magic.common.api.admin;

public record ScryfallCacheStatusResponse(
    boolean available,
    long cardCount,
    String lastImportedAt,
    String bulkDataType,
    boolean refreshEnabled
) {}
