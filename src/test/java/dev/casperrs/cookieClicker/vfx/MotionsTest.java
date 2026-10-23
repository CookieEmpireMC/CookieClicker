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
    void popAlwaysEndsAtRestScale() {
        PopSequence pop = new PopSequence(List.of(new PopSequence.Step(1.3, 0.7, 1.3, 2)));
        assertEquals(2, pop.steps().size());
        assertEquals(1.0, pop.steps().getLast().sy());
        assertEquals(5, pop.totalTicks());
    }
}
