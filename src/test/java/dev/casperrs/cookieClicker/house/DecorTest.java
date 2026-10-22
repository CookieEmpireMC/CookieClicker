package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** houses/decor.yml: bad entries are reported and skipped, the rest still counts (brief plot-polish, Decoration). */
class DecorTest {

    private static Decor.Result parse(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return Decor.parse(YamlFiles.toMap(y));
    }

    @Test
    void badEntriesAreReportedAndTheRestStillCounts() throws Exception {
        Decor.Result r = parse("""
                blocks:
                  - {at: [3, 0, 5], block: "minecraft:barrel[facing=up]"}
                  - {at: [3, 16, 5], block: "minecraft:lantern"}
                  - {at: [-1, 0, 5], block: "minecraft:lantern"}
                  - {at: 3, block: "minecraft:lantern"}
                  - {at: [4, 0, 5]}
                  - {at: [3, 0, 5], block: "minecraft:flower_pot"}
                  - {at: [6, 1, 6], block: "minecraft:lantern[hanging=false]"}
                """);
        assertEquals(5, r.problems().size(), r.problems().toString());
        assertEquals(List.of(new Decor.Block(3, 0, 5, "minecraft:barrel[facing=up]"),
                new Decor.Block(6, 1, 6, "minecraft:lantern[hanging=false]")), r.decor().blocks());
    }

    @Test
    void aBlocksValueThatIsNotAListGivesNoDecor() throws Exception {
        Decor.Result r = parse("blocks: barrel\n");
        assertTrue(r.decor().blocks().isEmpty());
        assertEquals(1, r.problems().size(), r.problems().toString());
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
}
