package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** upgrade-holograms: motion-driven pop keyframes and the two upgrade presets. */
class PopMotionTest {

    /** A spin of one turn per 100 ticks and a bob, as a function of "now + ticksAhead". */
    private static final class FakeMotion implements PopMotion {
        final long now;

        FakeMotion(long now) {
            this.now = now;
        }

        @Override
        public Vector3f translation(int ticksAhead) {
            return new Vector3f(0, 0.1f * (now + ticksAhead), 0);
        }

        @Override
        public Quaternionf leftRotation(int ticksAhead) {
            return new Quaternionf().rotationY((float) (2 * Math.PI * (now + ticksAhead) / 100.0));
        }
    }

    @Test
    void rotationAdvancesByEachStepsTicksAndScaleComesFromThePreset() {
        PopSequence seq = PopSequence.standard();
        Vector3f base = new Vector3f(0.8f, 0.8f, 0.8f);
        long now = 40;
        for (int i = 0; i < seq.steps().size(); i++) {
            PopSequence.Step step = seq.steps().get(i);
            long stepStart = now + seq.startTick(i);
            PopMotion.Frame f = PopMotion.frame(new FakeMotion(stepStart), base, step);
            long end = stepStart + step.ticks();
            assertEquals(step.ticks(), f.ticks());
            assertEquals(0.1f * end, f.translation().y, 1e-4);
            Quaternionf expected = new Quaternionf().rotationY((float) (2 * Math.PI * end / 100.0));
            assertTrue(f.leftRotation().difference(expected, new Quaternionf()).angle() < 1e-3
                            || Math.abs(f.leftRotation().dot(expected)) > 0.9999,
                    "rotation at step " + i);
            assertEquals(base.x * step.sx(), f.scale().x, 1e-5);
            assertEquals(base.y * step.sy(), f.scale().y, 1e-5);
        }
    }

    @Test
    void theRestStepSettlesOnTheMotionAtTheEndOfThePop() {
        PopSequence seq = PopSequence.standard();
        Vector3f base = new Vector3f(1, 1, 1);
        PopSequence.Step rest = seq.steps().getLast();
        long popStart = 100;
        PopMotion.Frame f = PopMotion.frame(new FakeMotion(popStart + seq.startTick(seq.steps().size() - 1)), base, rest);
        assertEquals(0.1f * (popStart + seq.totalTicks()), f.translation().y, 1e-4);
        assertEquals(1f, f.scale().x, 1e-6);
        assertEquals(1f, f.scale().y, 1e-6);
    }


    /** Spin and bob read at the server tick {@link #now}; the test moves the tick forward before each step. */
    private static final class ServerMotion implements PopMotion {
        long now;

        @Override
        public Vector3f translation(int ticksAhead) {
            return new Vector3f(0, 0.1f * (now + ticksAhead), 0);
        }

        @Override
        public Quaternionf leftRotation(int ticksAhead) {
            return new Quaternionf().rotationY((float) (2 * Math.PI * (now + ticksAhead) / 100.0));
        }
    }

    /** A Display stub that keeps its transformation and records every transformation and duration set on it. */
    private static final class RecordingDisplay {
        final List<Transformation> sent = new ArrayList<>();
        final List<Integer> durations = new ArrayList<>();
        Transformation current;
        final Display display;

        RecordingDisplay(Transformation start) {
            this.current = start;
            this.display = (Display) Proxy.newProxyInstance(Display.class.getClassLoader(),
                    new Class<?>[] {Display.class}, (proxy, m, args) -> switch (m.getName()) {
                        case "getTransformation" -> current;
                        case "setTransformation" -> {
                            current = (Transformation) args[0];
                            sent.add(current);
                            yield null;
                        }
                        case "setInterpolationDuration" -> {
                            durations.add((Integer) args[0]);
                            yield null;
                        }
                        case "isValid" -> true;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    });
        }
    }

    private static void assertVec(Vector3f expected, Vector3f actual, String what) {
        assertEquals(expected.x, actual.x, 1e-5, what + " x");
        assertEquals(expected.y, actual.y, 1e-5, what + " y");
        assertEquals(expected.z, actual.z, 1e-5, what + " z");
    }

    private static void assertQuat(Quaternionf expected, Quaternionf actual, String what) {
        assertEquals(expected.x, actual.x, 1e-5, what + " x");
        assertEquals(expected.y, actual.y, 1e-5, what + " y");
        assertEquals(expected.z, actual.z, 1e-5, what + " z");
        assertEquals(expected.w, actual.w, 1e-5, what + " w");
    }

    @Test
    void everyStepSendsTheMotionAtItsOwnEndTickAndThePresetScale() {
        Quaternionf rightRot = new Quaternionf().rotationZ(0.2f);
        RecordingDisplay d = new RecordingDisplay(
                new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f), rightRot));
        ServerMotion motion = new ServerMotion();
        AtomicInteger ended = new AtomicInteger();
        PopJob job = new PopJob(d.display, new Vector3f(0.8f), PopSequence.standard(), motion,
                ended2 -> ended.incrementAndGet());

        // the server tick moves on by one per call, as in the real ticker: step n is sent at tick startTick(n)
        for (int age = 0; age <= 7; age++) {
            motion.now = age;
            job.step();
        }

        // squash at tick 0 for 2 ticks: spin/bob at tick 2, scale 1.25/0.75/1.25 of the rest scale
        // stretch at tick 2 for 2 ticks: spin/bob at tick 4, scale 0.92/1.10/0.92
        // rest at tick 4 for 3 ticks: spin/bob at tick 7, scale back to the rest scale
        assertEquals(3, d.sent.size());
        assertEquals(List.of(2, 2, 3), d.durations);
        float[][] scales = {{1.0f, 0.6f, 1.0f}, {0.736f, 0.88f, 0.736f}, {0.8f, 0.8f, 0.8f}};
        int[] endTick = {2, 4, 7};
        for (int i = 0; i < 3; i++) {
            Transformation t = d.sent.get(i);
            assertVec(new Vector3f(0, 0.1f * endTick[i], 0), t.getTranslation(), "translation step " + i);
            assertQuat(new Quaternionf().rotationY((float) (2 * Math.PI * endTick[i] / 100.0)),
                    t.getLeftRotation(), "left rotation step " + i);
            assertVec(new Vector3f(scales[i][0], scales[i][1], scales[i][2]), t.getScale(), "scale step " + i);
            assertQuat(rightRot, t.getRightRotation(), "right rotation step " + i);
        }
        assertEquals(1, ended.get(), "onEnd runs once when the pop finishes");
    }

    @Test
    void aCutShortPopPutsTheRestScaleBackOnTheMotion() {
        RecordingDisplay d = new RecordingDisplay(
                new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f), new Quaternionf()));
        ServerMotion motion = new ServerMotion();
        PopJob job = new PopJob(d.display, new Vector3f(0.8f), PopSequence.standard(), motion, ended -> { });

        motion.now = 0;
        job.step();
        motion.now = 1;
        job.cancel(); // shutdown/reload between the first and second step

        // the cleanup step is one tick long, so it reads the motion one tick ahead
        assertEquals(2, d.sent.size());
        assertEquals(List.of(2, 1), d.durations);
        assertVec(new Vector3f(0.8f), d.sent.get(1).getScale(), "rest scale");
        assertVec(new Vector3f(0, 0.2f, 0), d.sent.get(1).getTranslation(), "translation at tick 2");
    }

    @Test
    void withoutAMotionThePopKeepsTheCurrentSpinAsBefore() {
        Vector3f translation = new Vector3f(0.3f, 0.4f, 0.5f);
        Quaternionf left = new Quaternionf().rotationY(0.7f);
        RecordingDisplay d = new RecordingDisplay(
                new Transformation(translation, left, new Vector3f(0.8f), new Quaternionf()));
        PopJob job = new PopJob(d.display, new Vector3f(0.8f), PopSequence.standard(), ended -> { });

        job.step();

        Transformation t = d.sent.getFirst();
        assertVec(translation, t.getTranslation(), "translation kept");
        assertQuat(left, t.getLeftRotation(), "left rotation kept");
        assertVec(new Vector3f(1.0f, 0.6f, 1.0f), t.getScale(), "scale from the preset");
    }
}
