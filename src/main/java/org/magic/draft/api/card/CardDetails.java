package org.magic.draft.api.card;

import java.util.List;
import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "name", "set", "set_name", "scryfall_id", "image_small", "image_normal", "image_flip" })
public class CardDetails {
    private String set;
    private String set_name;
    private String scryfall_id;
    private String image_small;
    private String image_normal;
    private String imageFlip;
    private String name;
    private Integer cmc;
    private List<String> parsed_cost;

    @JsonCreator
    @BsonCreator
    public CardDetails(@JsonProperty("set")          @BsonProperty("set")          final String set,
                       @JsonProperty("set_name")     @BsonProperty("set_name")     final String setName,
                       @JsonProperty("scryfall_id")  @BsonProperty("scryfall_id")  final String scryfallId,
                       @JsonProperty("image_small")  @BsonProperty("image_small")  final String imageSmall,
                       @JsonProperty("image_normal") @BsonProperty("image_normal") final String imageNormal,
                       @JsonProperty("image_flip")   @BsonProperty("image_flip")   final String imageFlip,
                       @JsonProperty("name")         @BsonProperty("name")         final String name,
                       @JsonProperty("cmc")          @BsonProperty("cmc")          final Integer cmc,
                       @JsonProperty("parsed_cost")  @BsonProperty("parsed_cost")  final List<String> parsed_cost) {
        this.set = Objects.requireNonNull(set, "set Required for card details");
        this.set_name = Objects.requireNonNull(setName, "set_name Required for card details");
        this.scryfall_id = Objects.requireNonNull(scryfallId, "scryfallId Required for card details");
        this.image_small = Objects.requireNonNull(imageSmall, "imageSmall Required for card details");
        this.image_normal = Objects.requireNonNull(imageNormal, "imageNormal Required for card details");
        // Only required for Flip Cards
        this.imageFlip = imageFlip;
        if (name == null) {
            System.out.println("\n\n\n\n\n!!!!!!!!!!!!!!!!!!!!\nCARD NAME IS FUCKED:\n" + scryfallId +"\n: \n\n\n\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!11");
        }
        this.name = Objects.requireNonNull(name, "name Required for card details");
        if (cmc == null) {
            System.out.println("\n\n\n\n\n!!!!!!!!!!!!!!!!!!!!\nCARD CMC IS FUCKED:\n" + scryfallId +"\n: \n\n\n\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!11");
        }
        this.cmc = Objects.requireNonNull(cmc, "cmc Required for card details");
        this.parsed_cost = Objects.requireNonNullElse(parsed_cost, List.of());
    }

    public String getSet() {
        return set;
    }

    public void setSet(String set) {
        this.set = set;
    }

    public Integer getCmc() {
        return this.cmc;
    }

    public String getImage_flip() {
        return imageFlip;
    }

    public String getSet_name() {
        return set_name;
    }

    public void setSet_name(String set_name) {
        this.set_name = set_name;
    }

    public String getScryfall_id() {
        return scryfall_id;
    }

    public void setScryfall_id(String scryfall_id) {
        this.scryfall_id = scryfall_id;
    }

    public String getImage_small() {
        return image_small;
    }

    public void setImage_small(String image_small) {
        this.image_small = image_small;
    }

    public String getImage_normal() {
        return image_normal;
    }

    public void setImage_normal(String image_normal) {
        this.image_normal = image_normal;
    }

    public String getName() {
        return name;
    }

    public void setParsed_cost(List<String> parsed_cost) {
        this.parsed_cost = parsed_cost;
    }

    public List<String> getParsed_cost() {
        return this.parsed_cost;
    }
}