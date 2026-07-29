package org.magic.pyramidDraft.api.card;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.magic.common.api.scryfall.ScryfallCard;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

class CardTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ScryfallCard loadCard(String fileName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            return MAPPER.readValue(is, ScryfallCard.class);
        }
    }

    @Test
    void testFromScryfallCardNormal() throws IOException {
        ScryfallCard card = loadCard("ScryfallLightningBolt.json");
        Card result = Card.fromScryfallCard(card);

        assertEquals("7673784e-db4b-43a1-8d55-1bb9fc1e284f", result.getCardID());
        assertEquals("Lightning Bolt", result.getName());
        assertEquals(1, result.getCmc());
        assertEquals("Instant", result.getType_line());
        assertNotNull(result.getCardDetails());
        assertEquals("Lightning Bolt", result.getCardDetails().getName());
        assertEquals("msc", result.getCardDetails().getSet());
    }

    @Test
    void testFromScryfallCardTransform() throws IOException {
        ScryfallCard card = loadCard("ScryfallArlinnKord.json");
        Card result = Card.fromScryfallCard(card);

        assertEquals("e72d7c11-2165-4c72-80f3-3c1a7b4b5572", result.getCardID());
        assertEquals("Arlinn Kord // Arlinn, Embraced by the Moon", result.getName());
        assertEquals(4, result.getCmc());
        assertEquals("Legendary Planeswalker — Arlinn // Legendary Planeswalker — Arlinn", result.getType_line());
        assertNotNull(result.getCardDetails().getImageFlip());
    }

    @Test
    void testFromScryfallCardMeldWithoutBackImage() throws IOException {
        ScryfallCard card = loadCard("ScryfallGisela.json");
        Card result = Card.fromScryfallCard(card);

        assertEquals("04506bad-3856-4184-8dda-941ded60f41a", result.getCardID());
        assertEquals("Gisela, the Broken Blade", result.getName());
        assertEquals(4, result.getCmc());
        assertEquals("Legendary Creature — Angel Horror", result.getType_line());
        assertNull(result.getCardDetails().getImageFlip());
    }

    @Test
    void testFromScryfallCardMeldWithBackImage() throws IOException {
        ScryfallCard card = loadCard("ScryfallGisela.json");
        ScryfallCard brisela = loadCard("ScryfallBrisela.json");
        Card result = Card.fromScryfallCard(card, brisela.imageUris());

        assertEquals("04506bad-3856-4184-8dda-941ded60f41a", result.getCardID());
        assertEquals("Gisela, the Broken Blade", result.getName());
        assertEquals("https://cards.scryfall.io/normal/front/7/e/7e926e19-553c-470a-afde-358541af5caa.jpg?1783908189", result.getCardDetails().getImageFlip());
    }
}
