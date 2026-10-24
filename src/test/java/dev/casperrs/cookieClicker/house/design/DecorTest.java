package dev.casperrs.cookieClicker.house.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** houses/decor.yml: bad entries are reported and skipped, the rest still counts. */
class DecorTest {

    private static Decor.Result parse(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return Decor.parse(YamlFiles.toMap(y));
    }

    @Test
    void theShippedDecorFileParsesWithoutProblems() throws Exception {
        Path file = Path.of("src/main/resources/houses/decor.yml");
        assumeTrue(Files.isRegularFile(file), "houses/decor.yml is not on this branch yet");
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(file));
        Decor.Result r = Decor.parse(YamlFiles.toMap(y));
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        assertFalse(r.decor().blocks().isEmpty());
    }

    @Test
    void groundDecorAtMinusOneAndMinusTwoParsesAndMinusThreeIsSkipped() throws Exception {
        Decor.Result r = parse("blocks:\n"
                + "  - {at: [1, -1, 1], block: \"minecraft:dirt_path\"}\n"
                + "  - {at: [2, -2, 1], block: \"minecraft:gravel\"}\n"
                + "  - {at: [3, -3, 1], block: \"minecraft:gravel\"}\n");
        assertEquals(2, r.decor().blocks().size());
        assertEquals(1, r.problems().size(), r.problems().toString());
    }
}
