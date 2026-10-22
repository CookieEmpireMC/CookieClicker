package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HouseSettingsTest {

    private static HouseSettings parse(String yaml, List<String> problems) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return HouseSettings.from(c, problems::add);
    }

    @Test
    void emptyConfigGivesTheDocumentedDefaults() throws Exception {
        List<String> problems = new ArrayList<>();
        HouseSettings s = parse("", problems);
        HouseSettings.Cinematic c = s.cinematic();
        assertTrue(c.enabled());
        assertEquals(HouseSettings.Trigger.ENTER_PLOT, c.trigger());
        assertEquals(HouseSettings.KeepOut.HOLD, c.keepOut());
        assertEquals(2.25, c.blocksPerTick(), 1e-9);
        assertEquals(5, c.layerDelayTicks());
        assertTrue(c.teleportToViewpoint());
        assertEquals(24, c.maxFallingDisplays());
        assertEquals(3, c.hammerEvery());
        assertTrue(s.chimney().smoke());
        assertEquals(60, s.chimney().crackleTicks());
        assertEquals(new HouseSettings.Steam(true, 7), s.steam());
        assertEquals(new HouseSettings.Ambient(true, 80, 24.0), s.ambient());
        assertTrue(problems.isEmpty());
    }

    @Test
    void shippedConfigMatchesTheCodeDefaults() throws Exception {
        YamlConfiguration shipped = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/config.yml")) {
            shipped.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> problems = new ArrayList<>();
        HouseSettings fromFile = HouseSettings.from(shipped, problems::add);
        assertEquals(parse("", new ArrayList<>()), fromFile, "config.yml and the in-code defaults must agree");
        assertTrue(problems.isEmpty(), problems.toString());
    }

    @Test
    void blocksPerTickIsADoubleAndClamped() throws Exception {
        assertEquals(0.5, parse("house:\n  cinematic:\n    blocks-per-tick: 0.5\n", new ArrayList<>())
                .cinematic().blocksPerTick(), 1e-9);
        assertEquals(0.05, parse("house:\n  cinematic:\n    blocks-per-tick: 0\n", new ArrayList<>())
                .cinematic().blocksPerTick(), 1e-9);
        assertEquals(0.05, parse("house:\n  cinematic:\n    blocks-per-tick: -3\n", new ArrayList<>())
                .cinematic().blocksPerTick(), 1e-9);
        assertEquals(64.0, parse("house:\n  cinematic:\n    blocks-per-tick: 5000\n", new ArrayList<>())
                .cinematic().blocksPerTick(), 1e-9);
    }

    @Test
    void maxFallingDisplaysNeverExceedsTheEngineCap() throws Exception {
        assertEquals(24, parse("house:\n  cinematic:\n    max-falling-displays: 500\n", new ArrayList<>())
                .cinematic().maxFallingDisplays());
        assertEquals(1, parse("house:\n  cinematic:\n    max-falling-displays: 0\n", new ArrayList<>())
                .cinematic().maxFallingDisplays());
    }

    @Test
    void otherValuesAreClamped() throws Exception {
        HouseSettings.Cinematic c = parse("""
                house:
                  cinematic:
                    slow-amount: -5
                    fake-air-per-tick: 1
                    refresh-hidden-ticks: -1
                    reassert-radius: 99
                    hammer-every: -4
                    viewpoint-radius: 0
                    confetti-height: 500
                    layer-delay-ticks: 100000
                  chimney:
                    crackle-every: -1
                """, new ArrayList<>()).cinematic();
        assertEquals(-1.0, c.slowAmount(), 1e-9);
        assertEquals(64, c.fakeAirPerTick());
        assertEquals(0, c.refreshHiddenTicks());
        assertEquals(16, c.reassertRadius());
        assertEquals(0, c.hammerEvery());
        assertEquals(0.5, c.viewpointRadius(), 1e-9);
        assertEquals(16.0, c.confettiHeight(), 1e-9);
        assertEquals(200, c.layerDelayTicks());
    }

    @Test
    void unknownEnumValuesFallBackAndReportAProblem() throws Exception {
        List<String> problems = new ArrayList<>();
        HouseSettings.Cinematic c = parse("house:\n  cinematic:\n    trigger: sometimes\n    keep-out: teleport\n",
                problems).cinematic();
        assertEquals(HouseSettings.Trigger.ENTER_PLOT, c.trigger());
        assertEquals(HouseSettings.KeepOut.HOLD, c.keepOut());
        assertEquals(2, problems.size(), problems.toString());
        assertFalse(problems.get(0).isBlank());
    }

    @Test
    void trueAndFalseForSmokeAndCinematic() throws Exception {
        HouseSettings s = parse("house:\n  cinematic:\n    enabled: false\n  chimney:\n    smoke: false\n",
                new ArrayList<>());
        assertFalse(s.cinematic().enabled());
        assertFalse(s.chimney().smoke());
    }
}
