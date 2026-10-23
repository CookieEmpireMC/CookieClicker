package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Malformed-config error paths: the parser must report and skip, never throw (reviewer-owned). */
class EffectsConfigEdgeTest {

    private static EffectsConfig parse(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return EffectsConfigParser.parse(y, null);
    }

    @Test
    void emptyFileFallsBackToShippedDefaults() throws Exception {
        EffectsConfig c = EffectsConfigParser.parse(new YamlConfiguration(), EffectsConfigParserTest.shipped());
        for (String id : new String[] {"cookie_click", "cookie_crit", "purchase", "pop"}) {
            assertNotNull(c.preset(id), id);
        }
    }

    @Test
    void outOfRangeLimitsAreClampedOrReported() throws Exception {
        EffectsConfig c = parse("""
                limits:
                  max-particles-per-effect: -3
                  max-display-entities-per-player: 9999
                  max-display-entities-global: banana
                  surprise: 1
                """);
        assertTrue(c.limits().maxParticlesPerEffect() >= 1);
        assertTrue(c.limits().maxDisplayEntitiesPerPlayer() <= 64);
        assertEquals(150, c.limits().maxDisplayEntitiesGlobal(), "unparsable falls back to the default");
        assertFalse(c.problems().isEmpty());
    }

    @Test
    void garbageShapesNeverThrow() throws Exception {
        EffectsConfig c = assertDoesNotThrow(() -> parse("""
                bad id!:
                  layers: [ { type: particle, particle: dust } ]
                not_a_list:
                  layers: 5
                no_layers: {}
                scalar_effect: 7
                layer_not_a_map:
                  layers: [ 1, "x", null ]
                nan_points:
                  layers:
                    - { type: particle, particle: dust, shape: ring, points: .nan, radius: .inf, duration-ticks: -4 }
                ok:
                  layers: [ { type: particle, particle: end_rod } ]
                """));
        assertNotNull(c.preset("ok"));
        assertNull(c.preset("bad id!"));
        assertNull(c.preset("not_a_list"));
        assertNull(c.preset("no_layers"));
        assertNull(c.preset("scalar_effect"));
        assertNull(c.preset("layer_not_a_map"));
        assertFalse(c.problems().isEmpty());
        EffectsConfig.Preset nan = c.preset("nan_points");
        if (nan != null) {
            assertTrue(nan.particleCost() <= c.limits().maxParticlesPerEffect());
        }
    }

    @Test
    void zeroTickPopStepsAreHandled() throws Exception {
        EffectsConfig c = parse("""
                p:
                  layers: [ { type: scale-pop, steps: [ { scale: 1.2, ticks: 0 }, { scale: 1, ticks: -1 } ] } ]
                """);
        var pop = (dev.casperrs.cookieClicker.vfx.config.LayerSpec.ScalePop) c.preset("p").layers().getFirst();
        // PopJob sends one step per tick, so every step must last at least one tick
        for (var step : pop.sequence().steps()) {
            assertTrue(step.ticks() >= 1, "ticks " + step.ticks());
        }
    }
}
