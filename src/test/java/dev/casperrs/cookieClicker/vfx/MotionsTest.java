package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import dev.casperrs.cookieClicker.vfx.shape.Motions;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import java.util.List;
import org.junit.jupiter.api.Test;

class MotionsTest {

    @Test
    void floatingTextRisesMonotonicallyAndEndsAtTheTop() {
        DisplayTrack track = Motions.floatingText(0.8, 20, 1.0, 0.5, 1.15, 3, 6, 1);
        assertEquals(20, track.removeTick());
        double previous = -1;
        for (int t = 0; t <= 20; t++) {
            double y = track.renderedAt(t)[1];
            assertTrue(y >= previous - 1e-9, "never sinks (tick " + t + ")");
            previous = y;
        }
        assertEquals(0.8, track.renderedAt(20)[1], 1e-9);
        assertTrue(track.renderedAt(20)[3] < 0.05, "shrunk away at the end");
    }

    @Test
    void floatingTextEasesOut() {
        DisplayTrack track = Motions.floatingText(1.0, 20, 1.0, 0.5, 1.15, 3, 6, 1);
        double firstHalf = track.renderedAt(10)[1];
        assertTrue(firstHalf > 0.5, "more than half the rise in the first half, got " + firstHalf);
    }

    @Test
    void floatingTextPopsInWithOvershoot() {
        DisplayTrack track = Motions.floatingText(0.8, 20, 2.0, 0.5, 1.2, 3, 6, 1);
        assertEquals(1.0, track.spawn().scale(), 1e-9);
        assertEquals(2.4, track.renderedAt(4)[3], 1e-9, "peak of the pop-in");
        assertEquals(2.0, track.keyframes().get(1).scale(), 1e-9, "settles at the rest scale");
    }

    @Test
    void floatingTextKeyframesFitTheLifeAndOpacityStaysVisible() {
        DisplayTrack track = Motions.floatingText(0.8, 20, 1.0, 0.5, 1.15, 3, 6, 1);
        for (DisplayTrack.Keyframe k : track.keyframes()) {
            assertTrue(k.duration() >= 1);
            assertTrue(k.tick() + k.duration() <= track.removeTick());
        }
        for (DisplayTrack.OpacityStep s : track.opacity()) {
            assertTrue(s.opacity() >= 27 && s.opacity() <= 255, "client discards 4..26");
            assertTrue(s.tick() < track.removeTick());
        }
    }

    @Test
    void minimumTextDurationFitsAllKeyframes() {
        int min = Motions.minTextDuration(3, 6, 1);
        DisplayTrack track = Motions.floatingText(0.8, min, 1.0, 0.5, 1.15, 3, 6, 1);
        for (DisplayTrack.Keyframe k : track.keyframes()) {
            assertTrue(k.duration() >= 1, "keyframe at " + k.tick() + " has no time");
        }
    }

    @Test
    void crumbsFlyOutTumbleInSmallStepsAndShrink() {
        for (int i = 0; i < 5; i++) {
            DisplayTrack track = Motions.crumb(i, 5, 0.8, 70, 0.2, 0.5, 14, 0.22, 150, 1);
            List<DisplayTrack.Keyframe> keys = track.keyframes();
            assertEquals(2, keys.size());
            assertTrue(keys.get(0).angleDeg() < 120, "first tumble step under 120 degrees");
            assertTrue(keys.get(1).angleDeg() - keys.get(0).angleDeg() < 120, "second step under 120 degrees");
            double[] end = track.renderedAt(track.removeTick());
            assertTrue(Math.hypot(end[0], end[2]) > 0.1 || i == 0, "moved away from the origin");
            assertTrue(end[3] < 0.01, "shrunk away");
            assertTrue(track.removeTick() <= 14);
            double axisLen = Math.sqrt(keys.get(0).ax() * keys.get(0).ax() + keys.get(0).az() * keys.get(0).az());
            assertEquals(1.0, axisLen, 1e-9, "rotation axis is normalised");
        }
    }

    @Test
    void standardPopIsTwoTwoThreeTicks() {
        PopSequence pop = PopSequence.standard();
        assertEquals(3, pop.steps().size());
        assertEquals(0, pop.startTick(0));
        assertEquals(2, pop.startTick(1));
        assertEquals(4, pop.startTick(2));
        assertEquals(7, pop.totalTicks());
        PopSequence.Step last = pop.steps().getLast();
        assertEquals(1.0, last.sx());
        assertEquals(1.0, last.sy());
        assertEquals(1.0, last.sz());
    }

    @Test
    void popAlwaysEndsAtRestScale() {
        PopSequence pop = new PopSequence(List.of(new PopSequence.Step(1.3, 0.7, 1.3, 2)));
        assertEquals(2, pop.steps().size());
        assertEquals(1.0, pop.steps().getLast().sy());
        assertEquals(5, pop.totalTicks());
    }
}
