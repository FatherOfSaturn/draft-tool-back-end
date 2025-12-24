package org.magic.draft.api.scryfall.cardSearchParts;

import java.util.UUID;

public class AllPart {
    private String object;
    private UUID id;
    private String component;
    private String name;
    private String typeLine;
    private String uri;

    public String getObject() { return object; }
    public void setObject(String value) { this.object = value; }

    public UUID getId() { return id; }
    public void setId(UUID value) { this.id = value; }

    public String getComponent() { return component; }
    public void setComponent(String value) { this.component = value; }

    public String getName() { return name; }
    public void setName(String value) { this.name = value; }

    public String getTypeLine() { return typeLine; }
    public void setTypeLine(String value) { this.typeLine = value; }

    public String getUri() { return uri; }
    public void setUri(String value) { this.uri = value; }
}
