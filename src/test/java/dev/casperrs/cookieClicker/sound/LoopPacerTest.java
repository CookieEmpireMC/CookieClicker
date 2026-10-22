package dev.casperrs.cookieClicker.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Looping ambient sounds replay on their own period and never double (brief plot-polish, Ambient sound). */
class LoopPacerTest {

    private static final UUID ALICE = new UUID(1, 1);
    private static final UUID BOB = new UUID(2, 2);

    @Test
    void aLoopWaitsItsFullPeriodBeforeItPlaysAgain() {
        LoopPacer p = new LoopPacer();
        assertTrue(p.tryPlay(ALICE, "belt_loop", 100, 1000));
        assertFalse(p.tryPlay(ALICE, "belt_loop", 100, 1050));
        assertFalse(p.tryPlay(ALICE, "belt_loop", 100, 1099));
        assertTrue(p.tryPlay(ALICE, "belt_loop", 100, 1100));
        assertFalse(p.tryPlay(ALICE, "belt_loop", 100, 1150)); // the beat is kept: the next play is at 1200
        assertTrue(p.tryPlay(ALICE, "belt_loop", 100, 1200));
    }

    @Test
    void aRestartedTimerWaitsForTheRunningPlayAndKeepsTheBeat() {
        LoopPacer p = new LoopPacer();
        assertTrue(p.tryPlay(ALICE, "factory_ambient", 80, 1000));
        // re-entering the plot 30 ticks later: the new timer must wait 50 more ticks, not play over the old one
        assertEquals(50, p.delay(ALICE, "factory_ambient", 80, 1030));
        assertFalse(p.tryPlay(ALICE, "factory_ambient", 80, 1030));
        assertEquals(0, p.delay(ALICE, "factory_ambient", 80, 1080));
        assertTrue(p.tryPlay(ALICE, "factory_ambient", 80, 1080));
    }

    @Test
    void loopsAndPlayersAreIndependentAndQuittingForgetsThem() {
        LoopPacer p = new LoopPacer();
        assertTrue(p.tryPlay(ALICE, "belt_loop", 100, 500));
        assertTrue(p.tryPlay(ALICE, "house_chimney_crackle", 60, 500));
        assertTrue(p.tryPlay(BOB, "belt_loop", 100, 500));
        p.forget(ALICE);
        assertEquals(0, p.delay(ALICE, "belt_loop", 100, 510));
        assertTrue(p.tryPlay(ALICE, "belt_loop", 100, 510));
        assertFalse(p.tryPlay(BOB, "belt_loop", 100, 510)); // Bob's own beat is untouched
    }
}
