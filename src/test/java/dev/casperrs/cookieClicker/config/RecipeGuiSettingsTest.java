package dev.casperrs.cookieClicker.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class RecipeGuiSettingsTest {

    private static YamlConfiguration load(String yaml) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return c;
    }

    @Test
    void anAbsentSectionGivesTheDefaultsWithoutProblems() {
        List<String> problems = new ArrayList<>();
        assertEquals(RecipeGuiSettings.DEFAULTS, RecipeGuiSettings.from(null, problems::add));
        assertTrue(problems.isEmpty());
    }

    @Test
    void oneBadSlotIsOneProblemAndTheRestIsKept() throws Exception {
        List<String> problems = new ArrayList<>();
        ConfigurationSection gui = load("slots: [10, 99, 11]\n");
        RecipeGuiSettings s = RecipeGuiSettings.from(gui, problems::add);
        assertEquals(List.of(10, 11), s.slots());
        assertEquals(1, problems.size(), problems.toString());
        assertEquals(-1, s.slot(2), "a recipe without a slot is not shown");
    }

    @Test
    void theShippedSectionLoadsAndCoversEveryShippedRecipe() throws Exception {
        List<String> problems = new ArrayList<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        RecipeGuiSettings s = RecipeGuiSettings.from(config.getConfigurationSection("recipes.gui"), problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        ConfigurationSection recipes = YamlConfiguration.loadConfiguration(new File("src/main/resources/upgrades.yml"))
                .getConfigurationSection("recipes");
        assertEquals(recipes.getKeys(false).size(), s.slots().size(), "one slot per recipe");
        for (String id : recipes.getKeys(false)) {
            assertTrue(s.fallbackItems().containsKey(id), "no fallback item for recipe " + id);
        }
        assertTrue(s.fallbackItems().containsKey(RecipeGuiSettings.LOCKED));
    }
}
