package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PlotSettingsTest {

    private static PlotSettings parse(String yaml, List<String> problems) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return PlotSettings.from(c, problems::add);
    }

    @Test
    void emptyConfigGivesDefaults() throws Exception {
        List<String> problems = new ArrayList<>();
        PlotSettings s = parse("", problems);
        assertEquals(120, s.show().maxPoints());
        assertEquals("plot_outline", s.show().plotEffect());
        assertEquals("house_outline", s.show().houseEffect());
        assertEquals(16, s.chimneyReach());
        assertTrue(problems.isEmpty());
    }

    @Test
    void maxPointsCannotGoBelowTheSmallestOutline() throws Exception {
        assertEquals(12, parse("plot:\n  show:\n    max-points: 4\n", new ArrayList<>()).show().maxPoints());
        assertEquals(12, parse("plot:\n  show:\n    max-points: -5\n", new ArrayList<>()).show().maxPoints());
        assertEquals(2000, parse("plot:\n  show:\n    max-points: 99999\n", new ArrayList<>()).show().maxPoints());
    }

    @Test
    void chimneyReachIsClamped() throws Exception {
        assertEquals(1, parse("plot:\n  chimney-reach: 0\n", new ArrayList<>()).chimneyReach());
        assertEquals(64, parse("plot:\n  chimney-reach: 1000\n", new ArrayList<>()).chimneyReach());
        assertEquals(8, parse("plot:\n  chimney-reach: 8\n", new ArrayList<>()).chimneyReach());
    }

    @Test
    void badEffectIdsFallBackToTheOutlinePresetsWithAProblem() throws Exception {
        List<String> problems = new ArrayList<>();
        PlotSettings s = parse("plot:\n  show:\n    plot-effect: \"Bad Id\"\n    house-effect: \"x/y\"\n", problems);
        assertEquals("plot_outline", s.show().plotEffect());
        assertEquals("house_outline", s.show().houseEffect());
        assertEquals(2, problems.size());
    }

    @Test
    void shippedConfigEffectsExistInEffectsYml() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            config.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        YamlConfiguration effects = new YamlConfiguration();
        try (var in = getClass().getClassLoader().getResourceAsStream("effects.yml")) {
            effects.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> problems = new ArrayList<>();
        PlotSettings s = PlotSettings.from(config, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        for (String id : new String[] {s.show().plotEffect(), s.show().houseEffect()}) {
            assertTrue(effects.contains(id) || effects.contains("effects." + id) || effects.contains("presets." + id),
                    id + " must be a preset in effects.yml");
        }
    }
}
