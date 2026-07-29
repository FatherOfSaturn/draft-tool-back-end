package org.magic.common.external;

import java.util.List;

import org.magic.pyramidDraft.api.card.Card;

/**
 * Result of a batch card lookup, containing successfully resolved cards
 * and a list of names that could not be found on Scryfall.
 */
public record BatchResult(List<Card> cards, List<String> notFound) {
}
