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
    void valuesAreClampedAndNeverBreakTheCurve() throws Exception {
        BeltSettings s = parse("""
                belt:
                  max-cookies: 5000
                  cookie-scale: 100
                  speed: {min: -3, max: 1e9}
                  interval: {min: -5, max: 99999}
                  sound-every: -4
                """, new ArrayList<>());
        assertEquals(BeltSettings.MAX_COOKIES, s.maxCookies());
        assertEquals(2.0, s.cookieScale());
        assertEquals(0.05, s.curve().speedMin());
        assertEquals(20.0, s.curve().speedMax());
        assertEquals(1, s.curve().intervalMin());
        assertEquals(1200, s.curve().intervalMax());
        assertEquals(0, s.soundEvery());
        assertEquals(0, parse("belt: {max-cookies: -2}", new ArrayList<>()).maxCookies());
    }

    @Test
    void invertedRangesAreReportedAndFixed() throws Exception {
        List<String> problems = new ArrayList<>();
        BeltSettings s = parse("belt:\n  speed: {min: 3, max: 1}\n  interval: {min: 50, max: 10}\n", problems);
        assertEquals(2, problems.size(), problems.toString());
        assertEquals(s.curve().speedMin(), s.curve().speedMax());
        assertEquals(s.curve().intervalMin(), s.curve().intervalMax());
    }

    @Test
    void badCpsScaleFallsBackToAThousand() throws Exception {
        for (String v : new String[] {"0", "-5", ".inf", ".nan"}) {
            List<String> problems = new ArrayList<>();
            BeltSettings s = parse("belt: {cps-scale: " + v + "}", problems);
            assertEquals(1000.0, s.curve().cpsScale(), v);
            assertFalse(problems.isEmpty(), v);
        }
    }

    @Test
    void garbageTypesDoNotThrow() throws Exception {
        parse("belt: {enabled: maybe, max-cookies: lots, cookie-scale: big, surface-yaw: spin, sound-every: x}",
                new ArrayList<>());
        parse("belt: 5", new ArrayList<>());
        BeltSettings s = parse("belt: {fallback-item: '  GRAY_CARPET '}", new ArrayList<>());
        assertEquals("GRAY_CARPET", s.fallbackItem());
        assertEquals(180.0, parse("belt: {surface-yaw: 540}", new ArrayList<>()).surfaceYaw());
    }

    @Test
    void disabledStaysDisabled() throws Exception {
        assertFalse(parse("belt: {enabled: false}", new ArrayList<>()).enabled());
    }

    @Test
    void cameraDefaultsToOnAndCanBeTurnedOff() throws Exception {
        assertTrue(HouseSettings.from(yaml(""), x -> {
        }).cinematic().camera());
        assertFalse(HouseSettings.from(yaml("house:\n  cinematic:\n    camera: false\n"), x -> {
        }).cinematic().camera());
        // a non-boolean must not throw
        HouseSettings.from(yaml("house:\n  cinematic:\n    camera: sure\n"), x -> {
        });
    }
}
