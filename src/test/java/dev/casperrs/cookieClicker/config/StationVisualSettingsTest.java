package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class StationVisualSettingsTest {

    private static YamlConfiguration yaml(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return y;
    }

    private static StationVisualSettings parse(String text, List<String> problems) {
        return StationVisualSettings.from(yaml(text), problems::add);
    }

    @Test
    void emptyConfigGivesDefaultsWithoutProblems() {
        List<String> problems = new ArrayList<>();
        StationVisualSettings s = parse("", problems);
        assertEquals(List.of(), problems);
        assertEquals(24, s.viewDistance());
        assertEquals(20, s.refreshTicks());
        assertEquals(10, s.cookie().segmentTicks());
        assertEquals(new Offset(0, 1.8, 2.5), s.cookie().offset());
        assertEquals("COOKIE", s.panel().fallbackFor("anything"));
        assertFalse(s.debugClicks());
    }

    @Test
    void shippedConfigYmlParsesCleanlyAndMatchesTheCodeDefaults() {
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        List<String> problems = new ArrayList<>();
        StationVisualSettings fromFile = StationVisualSettings.from(shipped, problems::add);
        assertEquals(List.of(), problems);
        StationVisualSettings defaults = parse("", new ArrayList<>());
        assertEquals(defaults.cookie(), fromFile.cookie(), "config.yml and the in-code defaults drifted apart");
        assertEquals(defaults.pedestal(), fromFile.pedestal());
        assertEquals(defaults.viewDistance(), fromFile.viewDistance());
        for (String state : List.of("header", "affordable", "unaffordable")) {
            assertEquals(defaults.panel().color(state), fromFile.panel().color(state), state);
        }
        assertEquals(0x90181818, fromFile.panel().color("locked"));
        assertEquals("BREAD", fromFile.panel().fallbackFor("villager_baker"));
        assertEquals("WHEAT", fromFile.panel().fallbackFor("wheat_farm"));
    }

}
