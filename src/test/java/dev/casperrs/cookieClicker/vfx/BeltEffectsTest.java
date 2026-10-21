package dev.casperrs.cookieClicker.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** conveyor-belt: the {@code belt_arrive} preset and the {@code min-interval-ticks} rate cap behind it. */
class BeltEffectsTest {

    private static EffectsConfig parse(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return EffectsConfigParser.parse(y, null);
    }

    @Test
    void beltArriveIsTinyAndRateCapped() throws Exception {
        EffectsConfig config = EffectsConfigParser.parse(EffectsConfigParserTest.shipped(), null);
        assertEquals(List.of(), config.problems());
        EffectsConfig.Preset arrive = config.preset("belt_arrive");
        assertNotNull(arrive, "contract ID belt_arrive");
        assertEquals(0, arrive.entityCost(), "no display entities: arrivals never touch the display caps");
        assertTrue(arrive.particleCost() <= 20, "cost " + arrive.particleCost());
        assertTrue(arrive.minIntervalTicks() >= 2, "rate-capped per viewer");
        double perSecond = arrive.particleCost() * 20.0 / arrive.minIntervalTicks();
        assertTrue(perSecond <= 100, "at most 100 particles/s per viewer, got " + perSecond);
    }

    @Test
    void minIntervalIsParsedAndBadValuesAreReported() throws Exception {
        EffectsConfig c = parse("""
                a:
                  min-interval-ticks: 6
                  layers:
                    - { type: particle, particle: smoke }
                b:
                  min-interval-ticks: -1
                  layers:
                    - { type: particle, particle: smoke }
                c:
                  min-interval-ticks: fast
                  layers:
                    - { type: particle, particle: smoke }
                d:
                  layers:
                    - { type: particle, particle: smoke }
                """);
        assertEquals(6, c.preset("a").minIntervalTicks());
        assertEquals(0, c.preset("b").minIntervalTicks());
        assertEquals(0, c.preset("c").minIntervalTicks());
        assertEquals(0, c.preset("d").minIntervalTicks());
        assertEquals(2, c.problems().size(), c.problems().toString());
        assertTrue(c.problems().stream().allMatch(p -> p.contains("min-interval-ticks")));
    }

    @Test
    void throttleAllowsOnePlayPerIntervalPerViewerAndEffect() {
        Throttle t = new Throttle();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        assertTrue(t.allow(alice, "belt_arrive", 100, 4));
        assertFalse(t.allow(alice, "belt_arrive", 101, 4));
        assertFalse(t.allow(alice, "belt_arrive", 103, 4));
        assertTrue(t.allow(bob, "belt_arrive", 103, 4), "other viewers have their own budget");
        assertTrue(t.allow(alice, "cookie_click", 103, 4), "other effects have their own budget");
        assertTrue(t.allow(alice, "belt_arrive", 104, 4), "interval passed");
        assertTrue(t.allow(null, "belt_arrive", 104, 4));
        assertFalse(t.allow(null, "belt_arrive", 105, 4), "viewer null shares one budget");
    }

    @Test
    void throttleCountsDroppedPlaysAndForgets() {
        Throttle t = new Throttle();
        UUID alice = UUID.randomUUID();
        int shown = 0;
        for (long tick = 0; tick < 20; tick++) { // a cookie every tick for 1 s
            if (t.allow(alice, "belt_arrive", tick, 4)) {
                shown++;
            }
        }
        assertEquals(5, shown, "1 s at interval 4 = 5 plays");
        assertTrue(t.allow(alice, "x", 0, 0), "interval 0 = no cap");
        assertEquals(1, t.size(), "uncapped plays are not stored");
        t.forget(alice);
        assertEquals(0, t.size());
        assertTrue(t.allow(alice, "belt_arrive", 20, 4));
        assertFalse(t.allow(alice, "belt_arrive", 21, 4));
        t.clear();
        assertTrue(t.allow(alice, "belt_arrive", 21, 4), "reload resets");
        assertTrue(t.allow(alice, "belt_arrive", 0, 4), "a tick counter going back never blocks forever");
    }
}
