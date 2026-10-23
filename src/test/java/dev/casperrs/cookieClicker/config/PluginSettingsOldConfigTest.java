package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * An admin's config.yml from before tidy (1.2.1, 414 lines, ~110 knobs that are constants now) must still load
 * without extra problems and give the same settings as the new default file: the removed keys are ignored and their
 * values equal the built-in ones.
 */
class PluginSettingsOldConfigTest {

    private static final Path DATA = Path.of("data");

    private static YamlConfiguration read(String resource) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (var in = PluginSettingsOldConfigTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return y;
    }

    @Test
    void aConfigFromBeforeTidyLoadsWithTheSameSettingsAndNoNewProblems() throws Exception {
        List<String> oldProblems = new ArrayList<>();
        List<String> newProblems = new ArrayList<>();
        PluginSettings old = PluginSettings.from(read("config-1.2.1.yml"), DATA, oldProblems::add);
        PluginSettings now = PluginSettings.from(read("config.yml"), DATA, newProblems::add);
        assertEquals(newProblems, oldProblems);
        assertEquals(now.database(), old.database());
        assertEquals(now.resourcePack(), old.resourcePack());
        assertEquals(now.hud(), old.hud());
        assertEquals(now.plot(), old.plot());
        assertEquals(now.house(), old.house());
        assertEquals(now.belt(), old.belt());
        assertEquals(now.visuals(), old.visuals());
        assertEquals(now, old);
    }

    @Test
    void anEmptyConfigStillGivesTheShippedSettings() throws Exception {
        List<String> problems = new ArrayList<>();
        PluginSettings empty = PluginSettings.from(new YamlConfiguration(), DATA, problems::add);
        PluginSettings shipped = PluginSettings.from(read("config.yml"), DATA, p -> { });
        assertEquals(shipped, empty);
    }
}
