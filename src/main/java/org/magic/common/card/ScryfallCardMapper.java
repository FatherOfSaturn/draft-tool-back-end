package org.magic.common.card;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.api.scryfall.ScryfallImageUris;

/**
 * Maps Scryfall cards into the internal {@link Card} / {@link CardDetails}
 * model. Owns the image-resolution rules that were previously duplicated across
 * four call paths in {@code ScryfallCardService}:
 *
 * <ul>
 *   <li>normal cards: images come from {@code image_uris}</li>
 *   <li>multi-faced cards: front image from {@code card_faces[0]}, flip image
 *       from {@code card_faces[1]}</li>
 *   <li>meld cards: the flip image is the meld result card's image, passed in
 *       by the caller once resolved</li>
 * </ul>
 *
 * The mana cost is taken from the card, falling back to the first face for
 * multi-faced cards, and split into individual symbols ({@code {2}{W}{U}} ->
 * {@code ["2", "w", "u"]}).
 */
@Mapper(componentModel = "cdi")
public abstract class ScryfallCardMapper {

    /**
     * Maps a Scryfall card to the internal model, without a separate meld
     * back-image (normal, multi-faced, and non-meld cards).
     */
    public Card toCard(final ScryfallCard card) {
        return toCard(card, null);
    }

    /**
     * Maps a Scryfall card to the internal model. For meld cards the caller
     * supplies the resolved meld result image as the flip image.
     *
     * @param card          the Scryfall card
     * @param meldImageUris the meld result's images, or {@code null}
     * @return the internal card representation
     */
    public Card toCard(final ScryfallCard card, final ScryfallImageUris meldImageUris) {
        return new Card(
                card.name(),
                toDetails(card, meldImageUris),
                card.id(),
                (int) card.cmc(),
                card.typeLine());
    }

    @Mapping(target = "setName", source = "card.setName")
    @Mapping(target = "scryfallId", source = "card.id")
    @Mapping(target = "type", source = "card.typeLine")
    @Mapping(target = "cmc", expression = "java((int) card.cmc())")
    @Mapping(target = "imageSmall", expression = "java(imageSmall(card))")
    @Mapping(target = "imageNormal", expression = "java(imageNormal(card))")
    @Mapping(target = "imageFlip", expression = "java(imageFlip(card, meldImageUris))")
    @Mapping(target = "parsed_cost", expression = "java(parseManaCost(manaCost(card)))")
    // The Lombok setters expose duplicate snake_case targets (set_name, ...);
    // the constructor-based mapping above is authoritative.
    @Mapping(target = "set_name", ignore = true)
    @Mapping(target = "scryfall_id", ignore = true)
    @Mapping(target = "image_small", ignore = true)
    @Mapping(target = "image_normal", ignore = true)
    public abstract CardDetails toDetails(ScryfallCard card, ScryfallImageUris meldImageUris);

    /** Maps a Scryfall card to details without a separate meld back-image. */
    public CardDetails toDetails(final ScryfallCard card) {
        return toDetails(card, null);
    }

    /** Small front image: top-level image_uris, else the first card face. */
    protected String imageSmall(final ScryfallCard card) {
        if (card.imageUris() != null) {
            return card.imageUris().small();
        }
        if (card.cardFaces() != null && !card.cardFaces().isEmpty()) {
            ScryfallImageUris firstFace = card.cardFaces().get(0).imageUris();
            if (firstFace != null) {
                return firstFace.small();
            }
        }
        return null;
    }

    /** Normal front image: top-level image_uris, else the first card face. */
    protected String imageNormal(final ScryfallCard card) {
        if (card.imageUris() != null) {
            return card.imageUris().normal();
        }
        if (card.cardFaces() != null && !card.cardFaces().isEmpty()) {
            ScryfallImageUris firstFace = card.cardFaces().get(0).imageUris();
            if (firstFace != null) {
                return firstFace.normal();
            }
        }
        return null;
    }

    /**
     * Flip (back) image: for multi-faced cards the second face's normal image;
     * a resolved meld result image takes precedence when provided.
     */
    protected String imageFlip(final ScryfallCard card, final ScryfallImageUris meldImageUris) {
        String flip = null;
        if (card.imageUris() == null && card.cardFaces() != null && card.cardFaces().size() > 1) {
            ScryfallImageUris secondFace = card.cardFaces().get(1).imageUris();
            if (secondFace != null) {
                flip = secondFace.normal();
            }
        }
        if (flip == null && meldImageUris != null) {
            flip = meldImageUris.normal();
        }
        return flip;
    }

    /** Mana cost: top-level mana_cost, else the first card face's. */
    protected String manaCost(final ScryfallCard card) {
        if (card.manaCost() != null) {
            return card.manaCost();
        }
        if (card.cardFaces() != null && !card.cardFaces().isEmpty()) {
            return card.cardFaces().get(0).manaCost();
        }
        return null;
    }

    /** Splits a mana cost into individual lowercase symbols: {2}{W}{U} -> [2, w, u]. */
    protected List<String> parseManaCost(final String manaCost) {
        if (manaCost == null || manaCost.isBlank()) {
            return List.of();
        }
        return Arrays.stream(manaCost.split("\\}\\{"))
                .map(symbol -> symbol.replace("{", "").replace("}", "").toLowerCase())
                .collect(Collectors.toList());
    }
}
