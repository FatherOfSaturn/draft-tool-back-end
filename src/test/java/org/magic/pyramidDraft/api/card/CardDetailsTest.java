package org.magic.pyramidDraft.api.card;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.magic.common.api.scryfall.ScryfallCard;
import org.magic.common.api.scryfall.ScryfallImageUris;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

class CardDetailsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ScryfallCard loadCard(String fileName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            return MAPPER.readValue(is, ScryfallCard.class);
        }
    }

    private String readResource(String fileName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void testNormalCard() throws IOException {
        ScryfallCard card = loadCard("ScryfallLightningBolt.json");
        CardDetails details = CardDetails.fromScryfallCard(card);

        assertEquals("7673784e-db4b-43a1-8d55-1bb9fc1e284f", details.getScryfall_id());
        assertEquals("Lightning Bolt", details.getName());
        assertEquals("msc", details.getSet());
        assertEquals("Marvel Super Heroes Commander", details.getSet_name());
        assertEquals("Instant", details.getType());
        assertEquals(1, details.getCmc());
        assertEquals(List.of("r"), details.getParsed_cost());
        assertEquals("https://cards.scryfall.io/small/front/7/6/7673784e-db4b-43a1-8d55-1bb9fc1e284f.jpg?1783903008", details.getImage_small());
        assertEquals("https://cards.scryfall.io/normal/front/7/6/7673784e-db4b-43a1-8d55-1bb9fc1e284f.jpg?1783903008", details.getImage_normal());
        assertNull(details.getImageFlip());
    }

    @Test
    void testTransformCard() throws IOException {
        ScryfallCard card = loadCard("ScryfallArlinnKord.json");
        CardDetails details = CardDetails.fromScryfallCard(card);

        assertEquals("e72d7c11-2165-4c72-80f3-3c1a7b4b5572", details.getScryfall_id());
        assertEquals("Arlinn Kord // Arlinn, Embraced by the Moon", details.getName());
        assertEquals("inr", details.getSet());
        assertEquals("Innistrad Remastered", details.getSet_name());
        assertEquals("Legendary Planeswalker — Arlinn // Legendary Planeswalker — Arlinn", details.getType());
        assertEquals(4, details.getCmc());
        assertEquals(List.of("2", "r", "g"), details.getParsed_cost());
        assertEquals("https://cards.scryfall.io/small/front/e/7/e72d7c11-2165-4c72-80f3-3c1a7b4b5572.jpg?1783908089", details.getImage_small());
        assertEquals("https://cards.scryfall.io/normal/front/e/7/e72d7c11-2165-4c72-80f3-3c1a7b4b5572.jpg?1783908089", details.getImage_normal());
        assertEquals("https://cards.scryfall.io/normal/back/e/7/e72d7c11-2165-4c72-80f3-3c1a7b4b5572.jpg?1783908089", details.getImageFlip());
    }

    @Test
    void testTransformBattle() throws IOException {
        ScryfallCard card = loadCard("ScryfallInvasionOfAlara.json");
        CardDetails details = CardDetails.fromScryfallCard(card);

        assertEquals("318c363b-61cc-4e2f-8f86-a4287539ea07", details.getScryfall_id());
        assertEquals("Invasion of Alara // Awaken the Maelstrom", details.getName());
        assertEquals("mom", details.getSet());
        assertEquals("March of the Machine", details.getSet_name());
        assertEquals("Battle — Siege // Sorcery", details.getType());
        assertEquals(5, details.getCmc());
        assertEquals(List.of("w", "u", "b", "r", "g"), details.getParsed_cost());
        assertEquals("https://cards.scryfall.io/small/front/3/1/318c363b-61cc-4e2f-8f86-a4287539ea07.jpg?1783951955", details.getImage_small());
        assertEquals("https://cards.scryfall.io/normal/front/3/1/318c363b-61cc-4e2f-8f86-a4287539ea07.jpg?1783951955", details.getImage_normal());
        assertEquals("https://cards.scryfall.io/normal/back/3/1/318c363b-61cc-4e2f-8f86-a4287539ea07.jpg?1783951955", details.getImageFlip());
    }

    @Test
    void testTransformSaga() throws IOException {
        ScryfallCard card = loadCard("ScryfallAzusasManyJourneys.json");
        CardDetails details = CardDetails.fromScryfallCard(card);

        assertEquals("e8a51d2a-1582-4bad-995c-e7fe9f810149", details.getScryfall_id());
        assertEquals("Azusa's Many Journeys // Likeness of the Seeker", details.getName());
        assertEquals("neo", details.getSet());
        assertEquals("Kamigawa: Neon Dynasty", details.getSet_name());
        assertEquals("Enchantment — Saga // Enchantment Creature — Human Monk", details.getType());
        assertEquals(2, details.getCmc());
        assertEquals(List.of("1", "g"), details.getParsed_cost());
        assertEquals("https://cards.scryfall.io/small/front/e/8/e8a51d2a-1582-4bad-995c-e7fe9f810149.jpg?1783923861", details.getImage_small());
        assertEquals("https://cards.scryfall.io/normal/front/e/8/e8a51d2a-1582-4bad-995c-e7fe9f810149.jpg?1783923861", details.getImage_normal());
        assertEquals("https://cards.scryfall.io/normal/back/e/8/e8a51d2a-1582-4bad-995c-e7fe9f810149.jpg?1783923861", details.getImageFlip());
    }

    @Test
    void testMeldCardWithoutBackImage() throws IOException {
        ScryfallCard card = loadCard("ScryfallGisela.json");
        CardDetails details = CardDetails.fromScryfallCard(card);

        assertEquals("04506bad-3856-4184-8dda-941ded60f41a", details.getScryfall_id());
        assertEquals("Gisela, the Broken Blade", details.getName());
        assertEquals("inr", details.getSet());
        assertEquals("Innistrad Remastered", details.getSet_name());
        assertEquals("Legendary Creature — Angel Horror", details.getType());
        assertEquals(4, details.getCmc());
        assertEquals(List.of("2", "w", "w"), details.getParsed_cost());
        assertEquals("https://cards.scryfall.io/small/front/0/4/04506bad-3856-4184-8dda-941ded60f41a.jpg?1783908181", details.getImage_small());
        assertEquals("https://cards.scryfall.io/normal/front/0/4/04506bad-3856-4184-8dda-941ded60f41a.jpg?1783908181", details.getImage_normal());
        assertNull(details.getImageFlip());
    }

    @Test
    void testMeldCardWithBackImage() throws IOException {
        ScryfallCard card = loadCard("ScryfallGisela.json");
        ScryfallCard brisela = loadCard("ScryfallBrisela.json");
        CardDetails details = CardDetails.fromScryfallCard(card, brisela.imageUris());

        assertEquals("04506bad-3856-4184-8dda-941ded60f41a", details.getScryfall_id());
        assertEquals("Gisela, the Broken Blade", details.getName());
        assertEquals("https://cards.scryfall.io/small/front/0/4/04506bad-3856-4184-8dda-941ded60f41a.jpg?1783908181", details.getImage_small());
        assertEquals("https://cards.scryfall.io/normal/front/0/4/04506bad-3856-4184-8dda-941ded60f41a.jpg?1783908181", details.getImage_normal());
        assertEquals("https://cards.scryfall.io/normal/front/7/e/7e926e19-553c-470a-afde-358541af5caa.jpg?1783908189", details.getImageFlip());
    }
}
