package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The segment maths of a falling block: fall, bounce with squash, settle. */
class DropTrackTest {

    private static final double EPS = 1e-9;
    private final DropTrack track = DropTrack.of(DropTrack.Params.DEFAULTS);

    @Test
    void defaultTimingIsFallBounceSettle() {
        List<DropTrack.Keyframe> k = track.keyframes();
        assertEquals(3, k.size());
        assertEquals(2, k.get(0).tick(), "first transformation 2 ticks after the spawn");
        assertEquals(6, k.get(0).duration());
        assertEquals(8, k.get(1).tick());
        assertEquals(10, k.get(2).tick());
        assertEquals(8, track.impactTick());
        assertEquals(12, track.landTick());
    }

    @Test
    void firstTransformationIsNeverOnTheSpawnTick() {
        DropTrack fast = DropTrack.of(new DropTrack.Params(1, 3, 1, 0, 1, 1, 1, 1, 1));
        assertTrue(fast.keyframes().getFirst().tick() >= 1);
        assertEquals(0, fast.spawn().tick());
    }

    @Test
    void neverTwoKeyframesInOneTick() {
        DropTrack shortest = DropTrack.of(new DropTrack.Params(1, 1, 1, 0.1, 1, 1.1, 0.9, 1.1, 1));
        Set<Integer> ticks = new HashSet<>();
        for (DropTrack.Keyframe k : shortest.keyframes()) {
            assertTrue(ticks.add(k.tick()), "tick " + k.tick() + " used twice");
            assertTrue(k.duration() >= 1);
        }
    }

    @Test
    void startsAboveFallsToTheTargetAndEndsExactlyAtRest() {
        assertArrayEquals(new double[] {0, 4, 0, 1, 1, 1}, track.renderedAt(0), EPS);
        assertArrayEquals(new double[] {0, 4, 0, 1, 1, 1}, track.renderedAt(2), EPS, "hangs until start-tick");
        assertEquals(0, track.renderedAt(track.impactTick())[1], EPS, "touches down at the impact tick");
        assertArrayEquals(new double[] {0, 0, 0, 1, 1, 1}, track.renderedAt(track.landTick()), EPS);
    }

    @Test
    void invalidParamsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DropTrack.Params(0, 4, 6, 0.25, 2, 1, 1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new DropTrack.Params(2, 4, 0, 0.25, 2, 1, 1, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new DropTrack.Params(2, 4, 6, 0.25, 2, 0, 1, 1, 2));
    }
}
