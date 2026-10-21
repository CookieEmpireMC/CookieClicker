package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import java.util.List;
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

    @Test
    void framesAreCopiesThePopMayKeep() {
        Vector3f t = new Vector3f(1, 2, 3);
        Quaternionf q = new Quaternionf();
        PopMotion same = new PopMotion() {
            public Vector3f translation(int a) {
                return t;
            }

            public Quaternionf leftRotation(int a) {
                return q;
            }
        };
        PopMotion.Frame f = PopMotion.frame(same, new Vector3f(1, 1, 1), PopSequence.standard().steps().getFirst());
        t.set(9, 9, 9);
        assertEquals(1f, f.translation().x);
    }

    @Test
    void oldOverloadsKeepTheirSignatures() throws Exception {
        assertNotNull(Effects.class.getMethod("pop", org.bukkit.entity.Display.class, org.bukkit.entity.Player.class));
        assertNotNull(Effects.class.getMethod("pop", org.bukkit.entity.Display.class, org.bukkit.entity.Player.class,
                String.class));
        assertNotNull(Effects.class.getMethod("pop", org.bukkit.entity.Display.class, org.bukkit.entity.Player.class,
                String.class, PopMotion.class));
    }

    @Test
    void upgradePresetsAreCheap() throws Exception {
        EffectsConfig config = EffectsConfigParser.parse(EffectsConfigParserTest.shipped(), null);
        assertEquals(List.of(), config.problems());
        for (String id : List.of("upgrade_bought", "upgrade_shelved")) {
            EffectsConfig.Preset p = config.preset(id);
            assertNotNull(p, "contract ID " + id);
            assertEquals(0, p.entityCost(), id);
            assertTrue(p.particleCost() <= 70, id + " cost " + p.particleCost());
        }
        assertTrue(config.preset("upgrade_shelved").particleCost() < config.preset("upgrade_bought").particleCost());
    }
}
