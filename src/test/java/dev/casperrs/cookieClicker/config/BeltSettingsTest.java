package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BeltSettingsTest {

    private static BeltSettings parse(String yaml, List<String> problems) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return BeltSettings.from(c, problems::add);
    }

    @Test
    void emptyConfigGivesTheDocumentedDefaults() throws Exception {
        List<String> problems = new ArrayList<>();
        BeltSettings s = parse("", problems);
        assertTrue(s.enabled());
        assertEquals(8, s.maxCookies());
        assertEquals(0.45, s.cookieScale(), 1e-12);
        assertEquals(100, s.soundEvery());
        assertEquals("BLACK_CARPET", s.fallbackItem());
        assertEquals(0.0, s.surfaceYaw());
        assertEquals(0.6, s.curve().speedMin(), 1e-12);
        assertEquals(2.5, s.curve().speedMax(), 1e-12);
        assertEquals(8, s.curve().intervalMin());
        assertEquals(80, s.curve().intervalMax());
        assertEquals(1000.0, s.curve().cpsScale(), 1e-12);
        assertTrue(problems.isEmpty());
    }

    @Test
    void shippedConfigYmlMatchesTheCodeDefaults() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/config.yml"));
        List<String> problems = new ArrayList<>();
        BeltSettings shipped = parse(text, problems);
        assertEquals(parse("", new ArrayList<>()), shipped);
        assertEquals(100, shipped.soundEvery());
        assertTrue(problems.isEmpty(), problems.toString());
        assertTrue(HouseSettings.from(yaml(text), x -> {
        }).cinematic().camera());
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(text);
        return c;
    }

    @Test
    void disabledStaysDisabled() throws Exception {
        assertFalse(parse("belt: {enabled: false}", new ArrayList<>()).enabled());
    }

}
