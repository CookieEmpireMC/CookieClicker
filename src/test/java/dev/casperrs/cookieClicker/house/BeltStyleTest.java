package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class BeltStyleTest {

    /** The shape of the shipped houses/belt.yml (model-designer). */
    private static final String GOOD = """
            direction: north
            surface-height: 1
            item-model: "cookieclicker:conveyor_belt"
            columns:
              roller: ["minecraft:spruce_log[axis=x]"]
              housing: ["minecraft:waxed_copper_grate[waterlogged=false]"]
            first: roller
            last: roller
            middle: [housing, roller]
            """;

    private static Map<String, Object> map(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return YamlFiles.toMap(y);
    }

    private static BeltStyle.Result parse(String text) throws Exception {
        return BeltStyle.parse(map(text));
    }

    @Test
    void parsesTheShippedShape() throws Exception {
        BeltStyle.Result r = parse(GOOD);
        assertTrue(r.problems().isEmpty(), r.problems().toString());
        BeltStyle s = r.style();
        assertNotNull(s);
        assertEquals(1, s.surfaceHeight());
        assertEquals(1.0, s.surface());
        assertEquals(List.of("minecraft:spruce_log[axis=x]"), s.column(0, 5));
        assertEquals(List.of("minecraft:spruce_log[axis=x]"), s.column(4, 5));
        // middle[(i - 1) % 2]: cell 1 housing, cell 2 roller, cell 3 housing
        assertEquals("minecraft:waxed_copper_grate[waterlogged=false]", s.column(1, 5).getFirst());
        assertEquals("minecraft:spruce_log[axis=x]", s.column(2, 5).getFirst());
        assertEquals("minecraft:waxed_copper_grate[waterlogged=false]", s.column(3, 5).getFirst());
        assertEquals(2, s.allBlockData().size());
    }

    @Test
    void singleCellBeltUsesTheFirstColumn() throws Exception {
        BeltStyle s = parse(GOOD).style();
        assertEquals(s.column(0, 1), List.of("minecraft:spruce_log[axis=x]"));
    }

    @Test
    void directionAndHeightAreOptionalButChecked() throws Exception {
        BeltStyle s = parse("columns: {a: [\"minecraft:stone\"]}\nfirst: a\nlast: a\nmiddle: a\n").style();
        assertNotNull(s);
        assertEquals(BeltStyle.DEFAULT_HEIGHT, s.surfaceHeight());
        assertNull(parse(GOOD.replace("direction: north", "direction: east")).style());
        assertNull(parse(GOOD.replace("surface-height: 1", "surface-height: 0")).style());
        assertNull(parse(GOOD.replace("surface-height: 1", "surface-height: 5")).style());
        assertNull(parse(GOOD.replace("surface-height: 1", "surface-height: 1.5")).style());
        assertNull(parse(GOOD.replace("surface-height: 1", "surface-height: tall")).style());
    }

    @Test
    void tallerFrameNeedsMatchingColumnLengths() throws Exception {
        String two = """
                surface-height: 2
                columns:
                  a: ["minecraft:stone", "minecraft:stone_slab"]
                  b: ["minecraft:stone"]
                first: a
                last: a
                middle: a
                """;
        BeltStyle.Result r = parse(two);
        assertNull(r.style());
        assertTrue(String.join("|", r.problems()).contains("columns.b"), r.problems().toString());
    }

    @Test
    void everyProblemIsCollectedNotJustTheFirst() throws Exception {
        BeltStyle.Result r = parse("direction: west\ncolumns: {a: [\"\"]}\nfirst: nope\nlast: nada\nmiddle: []\n");
        assertNull(r.style());
        assertTrue(r.problems().size() >= 4, r.problems().toString());
    }

    @Test
    void missingOrEmptySectionsAreProblemsNotCrashes() throws Exception {
        assertNull(parse("direction: north\n").style());
        assertNull(parse("columns: {}\nfirst: a\nlast: a\nmiddle: a\n").style());
        assertNull(parse("columns: [a, b]\nfirst: a\nlast: a\nmiddle: a\n").style());
        assertNull(BeltStyle.parse(Map.of()).style());
    }

    @Test
    void unknownColumnNamesInMiddleAreProblems() throws Exception {
        assertNull(parse(GOOD.replace("middle: [housing, roller]", "middle: [housing, ghost]")).style());
        assertNull(parse(GOOD.replace("middle: [housing, roller]", "middle: 5")).style());
    }

    @Test
    void noneStyleHasNoFrameBlocks() {
        BeltStyle none = BeltStyle.none();
        assertEquals(BeltStyle.DEFAULT_HEIGHT, none.surfaceHeight());
        assertTrue(none.column(0, 3).isEmpty());
        assertTrue(none.column(1, 3).isEmpty());
        assertTrue(none.allBlockData().isEmpty());
        assertEquals(3, BeltStyle.none(3).surfaceHeight());
    }

    @Test
    void theShippedBeltFileParsesWhenPresent() throws IOException {
        Path p = Path.of("src/main/resources/houses/belt.yml");
        assumeTrue(Files.isRegularFile(p), "belt.yml arrives with the model-designer merge");
        try {
            BeltStyle.Result r = BeltStyle.parse(map(Files.readString(p)));
            assertTrue(r.problems().isEmpty(), r.problems().toString());
            assertNotNull(r.style());
        } catch (InvalidConfigurationException e) {
            throw new IOException(e);
        }
    }
}
