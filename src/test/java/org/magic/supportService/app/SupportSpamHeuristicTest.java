package org.magic.supportService.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupportSpamHeuristicTest {

    @Test
    void shouldNotFlagShortMessages() {
        assertFalse(SupportSpamHeuristic.isLikelySpam("Crash when merging pyramid pack"));
        assertFalse(SupportSpamHeuristic.isLikelySpam(null));
        assertFalse(SupportSpamHeuristic.isLikelySpam(""));
    }

    @Test
    void shouldNotFlagNormalLongMessages() {
        String normal = "When I try to merge two pyramid packs during the third round, "
                + "the server returns a 500 error and the game freezes. It started "
                + "happening after yesterday's update. Reproduced on Chrome and Firefox.";
        assertTrue(normal.length() >= SupportSpamHeuristic.MIN_LENGTH);
        assertFalse(SupportSpamHeuristic.isLikelySpam(normal));
    }

    @Test
    void shouldNotFlagStackTracesOrCardNames() {
        String stackTrace = "java.lang.NullPointerException: Cannot invoke method on null "
                + "reference at org.magic.pyramidDraft.GameCoordination.merge "
                + "(GameCoordination.java:123) at io.vertx.ext.web.handler.impl";
        assertFalse(SupportSpamHeuristic.isLikelySpam(stackTrace));

        String cardNames = "Lurrus of the Dream-Den, Zhalso Githyanki, Jehu Lacernis, "
                + "Kynaios and Tiro, Sakashima of a Thousand Faces, Marchesa Blacksmythe";
        assertFalse(SupportSpamHeuristic.isLikelySpam(cardNames));
    }

    @Test
    void shouldFlagLongWhitespaceFreeRuns() {
        String run = "aaaaaaaaaa bbbbbbbbbb cccccccccc dddddddddd eeeeeeeeee "
                + "ffffffffffffgggggggggggghhhhhhhhhhhhiiiiiiiiiiiijjjjjjjjjjjjkkkkkkkkkkkkllllllllllllmmmmmmmmmm";
        assertTrue(run.length() >= SupportSpamHeuristic.MIN_LENGTH);
        assertTrue(SupportSpamHeuristic.isLikelySpam(run));
    }

    @Test
    void shouldFlagLowVowelRatioGibberish() {
        String gibberish = "xkcd qwrtz psjwv bnmhg fdtrlk zxcvb mnbvc xkcd qwrtz psjwv bnmhg fdtrlk zxcvb mnbvc";
        assertTrue(gibberish.length() >= SupportSpamHeuristic.MIN_LENGTH);
        assertTrue(SupportSpamHeuristic.isLikelySpam(gibberish));
    }

    @Test
    void shouldFlagLongUnspacedGibberish() {
        assertTrue(SupportSpamHeuristic.isLikelySpam(
                "qwrtypsdfghjklzxcvbnmqwrtypsdfghjklzxcvbnmqwrtypsdfghjklzxcvbnmqwrtypsdfghjklzxcvbnm"));
    }
}
