package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.house.RevealPlan.Phase;
import dev.casperrs.cookieClicker.house.RevealPlan.Pace;
import dev.casperrs.cookieClicker.plot.RegionShape;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevealPlanTest {

    /** {@code layers} layers of {@code w x w} blocks. */
    private static RevealOrder order(int w, int layers) {
        long[] b = new long[w * w * layers];
        int n = 0;
        for (int y = 0; y < layers; y++) {
            for (int z = 0; z < w; z++) {
                for (int x = 0; x < w; x++) {
                    b[n++] = RegionShape.pack(x, y, z);
                }
            }
        }
        return RevealOrder.of(b);
    }

    private static Pace pace(double perTick, int maxFalling, int layerDelay) {
        return new Pace(perTick, maxFalling, layerDelay, 4096, 0);
    }

    private static List<Integer> hideAll(RevealPlan plan) {
        List<Integer> sent = new ArrayList<>();
        plan.beginTick();
        plan.nextHide(sent::add);
        return sent;
    }

    private static List<Integer> dropsThisTick(RevealPlan plan) {
        List<Integer> out = new ArrayList<>();
        int i;
        while ((i = plan.nextDrop()) >= 0) {
            out.add(i);
        }
        return out;
    }

    @Test
    void hidingIsPacedBySendPerTickThenRevealingStarts() {
        RevealPlan plan = new RevealPlan(order(4, 1), new Pace(1, 4, 0, 6, 0)); // 16 blocks, 6 per tick
        Set<Integer> seen = new HashSet<>();
        int ticks = 0;
        while (plan.phase() == Phase.HIDING) {
            plan.beginTick();
            List<Integer> sent = new ArrayList<>();
            plan.nextHide(sent::add);
            assertTrue(sent.size() <= 6);
            seen.addAll(sent);
            assertEquals(-1, plan.nextDrop(), "nothing drops while hiding");
            ticks++;
        }
        assertEquals(3, ticks);
        assertEquals(16, seen.size());
        assertEquals(Phase.REVEALING, plan.phase());
        assertEquals(16, plan.hiddenCount());
    }

    @Test
    void layerDelayHoldsBackTheFirstDrop() {
        RevealPlan plan = new RevealPlan(order(2, 1), pace(1, 4, 5));
        hideAll(plan); // phase -> REVEALING with delay 5
        int firstDropTick = -1;
        for (int t = 1; t <= 10 && firstDropTick < 0; t++) {
            plan.beginTick();
            if (plan.nextDrop() >= 0) {
                firstDropTick = t;
            }
        }
        assertEquals(6, firstDropTick); // 5 delay ticks, the first block on the 6th
    }

    @Test
    void maxFallingCapsSimultaneousDrops() {
        RevealPlan plan = new RevealPlan(order(10, 1), pace(64, 3, 0));
        hideAll(plan);
        plan.beginTick();
        List<Integer> d = dropsThisTick(plan);
        assertEquals(3, d.size());
        assertEquals(3, plan.fallingCount());
        plan.beginTick();
        assertTrue(dropsThisTick(plan).isEmpty());
        assertTrue(plan.landed(d.get(0)));
        plan.beginTick();
        assertEquals(1, dropsThisTick(plan).size());
        assertEquals(3, plan.fallingCount());
    }

    @Test
    void aLayerStartsOnlyWhenTheOneBelowHasLandedPlusTheDelay() {
        RevealPlan plan = new RevealPlan(order(2, 2), pace(64, 100, 3)); // 2 layers of 4
        hideAll(plan);
        List<Integer> bottom = new ArrayList<>();
        for (int t = 0; t < 10; t++) {
            plan.beginTick();
            bottom.addAll(dropsThisTick(plan));
        }
        assertEquals(4, bottom.size(), "only the bottom layer is out");
        for (int i : bottom) {
            assertTrue(plan.bottomLayer(i));
            assertEquals(0, RegionShape.unpackY(plan.position(i)));
        }
        // three of four landed: still nothing
        bottom.subList(0, 3).forEach(plan::landed);
        for (int t = 0; t < 10; t++) {
            plan.beginTick();
            assertEquals(-1, plan.nextDrop());
        }
        plan.landed(bottom.get(3));
        int firstTop = -1;
        for (int t = 1; t <= 10 && firstTop < 0; t++) {
            plan.beginTick();
            if (plan.nextDrop() >= 0) {
                firstTop = t;
            }
        }
        assertEquals(4, firstTop, "3 delay ticks, then the first top block");
    }

    @Test
    void abortReturnsEveryStillHiddenOrFallingBlockExactlyOnce() {
        RevealPlan plan = new RevealPlan(order(3, 3), pace(64, 100, 0)); // 27 blocks
        hideAll(plan);
        plan.beginTick();
        List<Integer> d = dropsThisTick(plan); // bottom layer: 9 falling
        assertEquals(9, d.size());
        plan.landed(d.get(0));
        plan.landed(d.get(1));
        long[] restore = plan.abort();
        assertEquals(25, restore.length);
        Set<Long> set = new HashSet<>();
        for (long p : restore) {
            assertTrue(set.add(p), "no duplicates");
        }
        assertFalse(set.contains(plan.position(d.get(0))), "landed ones are already real");
        assertFalse(set.contains(plan.position(d.get(1))));
        for (int i = 0; i < plan.size(); i++) {
            if (i != d.get(0) && i != d.get(1)) {
                assertTrue(set.contains(plan.position(i)), "block " + i);
            }
        }
        assertEquals(Phase.DONE, plan.phase());
        assertEquals(0, plan.fallingCount());
        assertEquals(0, plan.hiddenCount());
        // after the abort nothing moves any more and a late landing is ignored
        assertFalse(plan.landed(d.get(2)));
        plan.beginTick();
        assertEquals(-1, plan.nextDrop());
        assertEquals(0, plan.abort().length, "abort twice is harmless");
        assertTrue(plan.complete());
    }

    @Test
    void abortWhileStillHidingReturnsEverythingEvenBlocksNotSentYet() {
        RevealPlan plan = new RevealPlan(order(4, 1), new Pace(1, 4, 0, 5, 0));
        plan.beginTick();
        plan.nextHide(i -> { });
        assertEquals(Phase.HIDING, plan.phase());
        assertEquals(16, plan.abort().length, "restoring a block that never went fake is harmless");
        assertEquals(Phase.DONE, plan.phase());
        List<Integer> late = new ArrayList<>();
        plan.nextHide(late::add);
        assertTrue(late.isEmpty());
    }

    @Test
    void resendCoversOnlyStillHiddenBlocksAndIsPaced() {
        RevealPlan plan = new RevealPlan(order(4, 1), new Pace(64, 100, 0, 5, 0));
        while (plan.phase() == Phase.HIDING) {
            plan.beginTick();
            plan.nextHide(i -> { });
        }
        plan.beginTick();
        int a = plan.nextDrop();
        plan.landed(a);
        plan.requestResend();
        Set<Integer> got = new HashSet<>();
        int ticks = 0;
        do {
            plan.beginTick();
            List<Integer> chunk = new ArrayList<>();
            plan.nextResend(chunk::add);
            assertTrue(chunk.size() <= 5);
            got.addAll(chunk);
            ticks++;
        } while (got.size() < 15 && ticks < 10);
        assertEquals(15, got.size());
        assertFalse(got.contains(a), "the revealed block is not made fake again");
        plan.beginTick();
        List<Integer> more = new ArrayList<>();
        plan.nextResend(more::add);
        assertTrue(more.isEmpty(), "one pass per request");
    }

    @Test
    void resendAfterAbortOrCompletionDoesNothing() {
        RevealPlan plan = new RevealPlan(order(2, 1), pace(1, 4, 0));
        hideAll(plan);
        plan.abort();
        plan.requestResend();
        plan.beginTick();
        List<Integer> r = new ArrayList<>();
        plan.nextResend(r::add);
        assertTrue(r.isEmpty());
    }

    @Test
    void aFullRunRevealsEveryBlockOnceInOrderAndCompletes() {
        RevealOrder o = order(5, 4); // 100 blocks
        RevealPlan plan = new RevealPlan(o, pace(2, 6, 2));
        List<Integer> started = new ArrayList<>();
        List<Integer> falling = new ArrayList<>();
        int ticks = 0;
        while (!plan.complete() && ticks++ < 2000) {
            plan.beginTick();
            plan.nextHide(i -> { });
            int i;
            while ((i = plan.nextDrop()) >= 0) {
                started.add(i);
                falling.add(i);
            }
            // land the oldest one after 3 ticks' worth, crudely: one per tick
            if (!falling.isEmpty() && ticks % 2 == 0) {
                plan.landed(falling.remove(0));
            }
            assertTrue(plan.fallingCount() <= 6);
        }
        assertTrue(plan.complete(), "finished in " + ticks + " ticks");
        assertEquals(100, started.size());
        for (int k = 0; k < started.size(); k++) {
            assertEquals(k, started.get(k), "drops follow the reveal order");
        }
        assertEquals(Phase.DONE, plan.phase());
    }

}
