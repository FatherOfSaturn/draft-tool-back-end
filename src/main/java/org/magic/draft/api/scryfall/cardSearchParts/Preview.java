package org.magic.draft.api.scryfall.cardSearchParts;

import java.time.LocalDate;

public class Preview {
    private String source;
    private String sourceUri;
    private LocalDate previewedAt;

    public String getSource() { return source; }
    public void setSource(String value) { this.source = value; }

    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String value) { this.sourceUri = value; }

    public LocalDate getPreviewedAt() { return previewedAt; }
    public void setPreviewedAt(LocalDate value) { this.previewedAt = value; }
}
