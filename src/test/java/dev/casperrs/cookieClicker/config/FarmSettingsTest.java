package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class FarmSettingsTest {

    private static FarmSettings parse(String yaml, List<String> problems) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return FarmSettings.from(c, problems::add);
    }

    @Test
    void theShippedConfigLoadsWithTheWheelSettings() {
        List<String> problems = new ArrayList<>();
        FarmSettings s = FarmSettings.from(
                YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml")), problems::add);
        assertTrue(problems.isEmpty(), problems::toString);
        assertEquals(2, s.wheelRpm(), 1e-12);
        assertEquals(64, s.wheelViewRange());
    }

    @Test
    void aPartialConfigFallsBackToTheWheelDefaults() throws Exception {
        List<String> problems = new ArrayList<>();
        FarmSettings s = parse("farm:\n  teleport-cooldown-seconds: 9\n", problems);
        assertEquals(9, s.teleportCooldownSeconds());
        assertEquals(2, s.wheelRpm(), 1e-12);
        assertEquals(64, s.wheelViewRange());
        assertTrue(problems.isEmpty(), problems::toString);
    }

    @Test
    void outOfRangeWheelValuesAreReportedAndReplaced() throws Exception {
        List<String> problems = new ArrayList<>();
        FarmSettings s = parse("farm:\n  wheel:\n    rpm: 11\n    view-range: 4\n", problems);
        assertEquals(2, s.wheelRpm(), 1e-12);
        assertEquals(64, s.wheelViewRange());
        assertEquals(2, problems.size(), problems::toString);
    }
}
