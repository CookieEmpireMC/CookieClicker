package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** The starter-house presets in the shipped effects.yml, the block-drop layer, loop-every and the new limits. */
class HouseEffectsConfigTest {

    /** The effect IDs in the starter-house contract, plus the preset behind Effects.dropBlock. */
    private static final List<String> CONTRACT = List.of("house_foundation_dust", "house_block_land",
            "house_sparkle", "house_confetti", "house_chimney_smoke", Effects.BLOCK_DROP);

    private static EffectsConfig parse(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return EffectsConfigParser.parse(y, null);
    }

    private static EffectsConfig shipped() throws Exception {
        return EffectsConfigParser.parse(EffectsConfigParserTest.shipped(), null);
    }

    @Test
    void everyContractPresetShipsWithoutProblems() throws Exception {
        EffectsConfig config = shipped();
        assertEquals(List.of(), config.problems());
        for (String id : CONTRACT) {
            assertNotNull(config.preset(id), id + " exists");
        }
    }

    @Test
    void perBlockEffectsAreCheapAndTheFinaleFitsTheCap() throws Exception {
        EffectsConfig config = shipped();
        // played for every block of a ~400-block house, a few blocks per tick
        assertTrue(config.preset("house_block_land").particleCost() <= 10);
        assertTrue(config.preset("house_sparkle").particleCost() <= 25);
        assertTrue(config.preset("house_foundation_dust").particleCost() <= 30);
        int confetti = config.preset("house_confetti").particleCost();
        assertTrue(confetti >= 100 && confetti <= config.limits().maxParticlesPerEffect(), "confetti " + confetti);
        for (String id : CONTRACT) {
            assertEquals(0, config.preset(id).entityCost(), id + " spawns no display entities through play()");
        }
    }

    @Test
    void chimneySmokeIsALoopSeenFromAfar() throws Exception {
        EffectsConfig.Preset smoke = shipped().preset("house_chimney_smoke");
        assertEquals(10, smoke.loopEvery());
        assertTrue(smoke.particleCost() <= 5, "a few long-lived puffs per play");
        for (LayerSpec layer : smoke.layers()) {
            assertTrue(assertInstanceOf(LayerSpec.Particles.class, layer).force(), layer.label() + " uses force");
        }
        assertEquals(0, shipped().preset("cookie_click").loopEvery(), "presets without loop-every report 0");
    }

    @Test
    void shippedBlockDropMatchesTheDefaults() throws Exception {
        LayerSpec.BlockDrop drop = assertInstanceOf(LayerSpec.BlockDrop.class,
                shipped().preset(Effects.BLOCK_DROP).layers().getFirst());
        assertEquals(DropTrack.of(DropTrack.Params.DEFAULTS), drop.track());
        assertEquals("house_block_land", drop.impactEffect());
        assertEquals(4, drop.impactParticles());
        assertTrue(drop.autoBrightness());
    }

    @Test
    void blockDropFieldsAreReadAndChecked() throws Exception {
        EffectsConfig c = parse("""
                d:
                  layers:
                    - { type: block-drop, start-tick: 1, drop-height: 6, fall-ticks: 8, bounce-height: 0,
                        bounce-ticks: 3, squash: 1.1, settle-ticks: 4, impact-effect: none,
                        impact-particles: 0, brightness: [15, 12], view-range: 0.5 }
                """);
        assertEquals(List.of(), c.problems());
        LayerSpec.BlockDrop d = (LayerSpec.BlockDrop) c.preset("d").layers().getFirst();
        assertEquals(1, d.track().keyframes().getFirst().tick());
        assertEquals(6, d.track().spawn().ty());
        assertEquals(9, d.track().impactTick());
        assertEquals(1 + 8 + 3 + 4, d.track().landTick());
        assertEquals(1.1, d.track().keyframes().get(1).sy(), 1e-9);
        assertNull(d.impactEffect());
        assertEquals(15, d.blockLight());
        assertEquals(12, d.skyLight());
        assertEquals(0.5f, d.viewRange());
    }

    @Test
    void badBlockDropValuesFallBackAndAreReported() throws Exception {
        EffectsConfig c = parse("""
                d:
                  layers:
                    - { type: block-drop, start-tick: 0, fall-ticks: abc, squash: [3, 0, 1],
                        brightness: [20, 5], impact-effect: "Bad Id!", drop-heigth: 3 }
                """);
        LayerSpec.BlockDrop d = (LayerSpec.BlockDrop) c.preset("d").layers().getFirst();
        DropTrack.Params def = DropTrack.Params.DEFAULTS;
        assertEquals(1, d.track().keyframes().getFirst().tick(), "start-tick clamped to 1");
        assertEquals(def.fallTicks(), d.track().keyframes().getFirst().duration());
        assertEquals(def.squashY(), d.track().keyframes().get(1).sy(), 1e-9);
        assertTrue(d.autoBrightness());
        assertNull(d.impactEffect());
        String all = String.join("\n", c.problems());
        for (String expected : List.of("start-tick", "fall-ticks", "squash", "brightness", "impact-effect",
                "drop-heigth")) {
            assertTrue(all.contains(expected), "reports " + expected + " in:\n" + all);
        }
    }

    @Test
    void badLoopEveryIsReportedAndIgnored() throws Exception {
        EffectsConfig c = parse("""
                s:
                  loop-every: 0
                  layers:
                    - { type: particle, particle: smoke }
                t:
                  loop-every: 15
                  layers:
                    - { type: particle, particle: smoke }
                """);
        assertEquals(0, c.preset("s").loopEvery());
        assertEquals(15, c.preset("t").loopEvery());
        assertEquals(1, c.problems().size(), c.problems().toString());
        assertTrue(c.problems().getFirst().contains("loop-every"));
    }

    @Test
    void newLimitsAreReadClampedAndDefaulted() throws Exception {
        EffectsConfig c = parse("""
                limits:
                  max-falling-blocks-per-player: 10
                  max-falling-blocks-global: -5
                """);
        assertEquals(10, c.limits().maxFallingBlocksPerPlayer());
        assertEquals(0, c.limits().maxFallingBlocksGlobal());
        assertEquals(EffectsConfig.Limits.DEFAULTS.maxLoopsPerPlayer(), c.limits().maxLoopsPerPlayer());
        assertEquals(1, c.problems().size(), c.problems().toString());
        assertEquals(24, EffectsConfig.Limits.DEFAULTS.maxFallingBlocksPerPlayer(), "contract default");
        assertEquals(EffectsConfig.Limits.DEFAULTS, new EffectsConfig.Limits(200, 8, 150));
        assertEquals(24, shipped().limits().maxFallingBlocksPerPlayer());
    }

    @Test
    void forceDefaultsToFalse() throws Exception {
        LayerSpec.Particles p = (LayerSpec.Particles) parse("""
                s:
                  layers:
                    - { type: particle, particle: smoke }
                """).preset("s").layers().getFirst();
        assertEquals(false, p.force());
    }
}
