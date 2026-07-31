package org.magic.common.api.scryfall;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BulkDataEntry(
    @JsonProperty("id") String id,
    @JsonProperty("type") String type,
    @JsonProperty("updated_at") String updatedAt,
    @JsonProperty("uri") String uri,
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("jsonl_download_uri") String jsonlDownloadUri,
    @JsonProperty("compressed_size") long compressedSize
) {
}
