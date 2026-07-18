package org.magic.accountService.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.magic.common.util.JsonUtility;

class DeckTest {

    @Test
    void shouldDeserializeFromFixture() throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream("Deck.json");
        String json = IOUtils.toString(is, "UTF-8");

        Deck deck = JsonUtility.getInstance().fromJson(json, Deck.class);

        assertNotNull(deck);
        assertEquals("507f1f77bcf86cd799439012", deck.getDeckID());
        assertEquals("507f1f77bcf86cd799439011", deck.getAccountID());
        assertEquals("Test Deck", deck.getName());
        assertEquals("A test deck", deck.getDescription());
        assertEquals(List.of("card1", "card2", "card3"), deck.getCardIds());
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), deck.getCreatedAt());
        assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), deck.getUpdatedAt());
    }

    @Test
    void shouldRoundTripSerializeDeserialize() throws IOException {
        Deck deck = new Deck();
        deck.setDeckID("507f1f77bcf86cd799439012");
        deck.setAccountID("507f1f77bcf86cd799439011");
        deck.setName("Test Deck");
        deck.setDescription("A test deck");
        deck.setCardIds(List.of("card1", "card2", "card3"));
        deck.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
        deck.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 0, 0, 0));

        String json = JsonUtility.getInstance().toJson(deck);
        Deck deserialized = JsonUtility.getInstance().fromJson(json, Deck.class);

        assertEquals(deck.getDeckID(), deserialized.getDeckID());
        assertEquals(deck.getAccountID(), deserialized.getAccountID());
        assertEquals(deck.getName(), deserialized.getName());
        assertEquals(deck.getDescription(), deserialized.getDescription());
        assertEquals(deck.getCardIds(), deserialized.getCardIds());
    }
}
