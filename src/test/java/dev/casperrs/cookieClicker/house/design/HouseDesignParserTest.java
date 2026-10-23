package dev.casperrs.cookieClicker.house.design;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HouseDesignParserTest {

    /** A valid 3x2x2 design: palette P (planks), _ (air); layer 0 has one keep cell. */
    private static Map<String, Object> base() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("size", List.of(3, 2, 2));
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("P", "minecraft:oak_planks");
        palette.put("_", "minecraft:air");
        m.put("palette", palette);
        m.put("layers", List.of(
                List.of("PP.", "P_P"),
                List.of("PPP", "___")));
        return m;
    }

    private static HouseDesignParser.Result parse(Map<String, Object> m) {
        return HouseDesignParser.parse("t", m);
    }

    private static String problems(Map<String, Object> m) {
        HouseDesignParser.Result r = parse(m);
        assertNull(r.design(), "expected a rejected design");
        assertFalse(r.problems().isEmpty());
        return String.join(" | ", r.problems());
    }

    @Test
    void parsesAValidDesignAndKeepsDotsAsKeepAndUnderscoreAsAir() {
        HouseDesign d = parse(base()).design();
        assertNotNull(d);
        assertEquals(3, d.sizeX());
        assertEquals(2, d.sizeY());
        assertEquals(2, d.sizeZ());
        assertArrayEquals(new int[] {0, 0, 0}, d.origin());
        assertEquals('.', d.grid()[0][0][2]);
        assertEquals('_', d.grid()[0][1][1]);
        List<HouseDesign.Cell> cells = d.cells();
        assertEquals(11, cells.size(), "12 cells minus one keep");
        assertTrue(cells.stream().noneMatch(c -> c.x() == 2 && c.y() == 0 && c.z() == 0), "keep cell is not a change");
        HouseDesign.Cell air = cells.stream().filter(c -> c.x() == 1 && c.y() == 0 && c.z() == 1).findFirst().orElseThrow();
        assertEquals("minecraft:air", air.blockData(), "_ is an explicit air placement, not skipped");
    }

    @Test
    void wrongRowLengthIsReportedWithLayerAndRow() {
        Map<String, Object> m = base();
        m.put("layers", List.of(List.of("PP.", "P_"), List.of("PPP", "___")));
        String p = problems(m);
        assertTrue(p.contains("layer 0 row 1"), p);
        assertTrue(p.contains("3 characters"), p);
    }

    @Test
    void unknownCharacterNamesTheCharacterAndPlace() {
        Map<String, Object> m = base();
        m.put("layers", List.of(List.of("PQ.", "P_P"), List.of("PPP", "___")));
        String p = problems(m);
        assertTrue(p.contains("'Q'") && p.contains("layer 0 row 0") && p.contains("not in the palette"), p);
    }

    @Test
    void sizeMustBeThreeWholePositiveNumbersWithinTheCap() {
        for (Object bad : new Object[] {List.of(1, 2), List.of(0, 1, 1), List.of(1.5, 1, 1), "9x7x7", List.of(257, 1, 1),
                List.of(-1, 1, 1), List.of("a", 1, 1)}) {
            Map<String, Object> m = base();
            m.put("size", bad);
            assertTrue(problems(m).contains("size must be"), String.valueOf(bad));
        }
        Map<String, Object> missing = base();
        missing.remove("size");
        assertTrue(problems(missing).contains("size must be"));
        Map<String, Object> huge = base();
        huge.put("size", List.of(HouseDesignParser.MAX_SIZE, 1, 1));
        huge.put("layers", List.of(List.of("P".repeat(HouseDesignParser.MAX_SIZE))));
        assertNotNull(parse(huge).design(), "the cap itself is allowed");
    }

    @Test
    void originOutOfBoundsOrMalformedIsReported() {
        for (Object bad : new Object[] {List.of(3, 0, 0), List.of(0, 2, 0), List.of(0, 0, 2), List.of(-1, 0, 0), List.of(0, 0), "x"}) {
            Map<String, Object> m = base();
            m.put("origin", bad);
            assertTrue(problems(m).contains("origin must be"), String.valueOf(bad));
        }
        Map<String, Object> ok = base();
        ok.put("origin", List.of(2, 1, 1));
        assertArrayEquals(new int[] {2, 1, 1}, parse(ok).design().origin());
    }

    @Test
    void signsAreOptionalAndValidated() {
        assertTrue(parse(base()).design().signs().isEmpty());

        Map<String, Object> ok = base();
        ok.put("signs", List.of(Map.of("at", List.of(0, 0, 0), "lines", List.of("", "Bakery"))));
        HouseDesign d = parse(ok).design();
        assertEquals(1, d.signs().size());
        assertEquals(List.of("", "Bakery"), d.signs().getFirst().lines());

        Map<String, Object> outside = base();
        outside.put("signs", List.of(Map.of("at", List.of(3, 0, 0), "lines", List.of("x"))));
        assertTrue(problems(outside).contains("signs[0].at"));

        Map<String, Object> onKeep = base();
        onKeep.put("signs", List.of(Map.of("at", List.of(2, 0, 0), "lines", List.of("x")))); // that cell is '.'
        assertTrue(problems(onKeep).contains("is not a palette block"));

        for (Object lines : new Object[] {List.of(), List.of("1", "2", "3", "4", "5"), "text"}) {
            Map<String, Object> m = base();
            m.put("signs", List.of(Map.of("at", List.of(0, 0, 0), "lines", lines)));
            assertTrue(problems(m).contains("signs[0].lines"), String.valueOf(lines));
        }

        Map<String, Object> notAMap = base();
        notAMap.put("signs", List.of("sign"));
        assertTrue(problems(notAMap).contains("signs[0] must be a section"));

        Map<String, Object> notAList = base();
        notAList.put("signs", "sign");
        assertTrue(problems(notAList).contains("signs must be a list"));
    }

    @Test
    void signTextIsKeptAsPlainStringsEvenWhenItLooksLikeMiniMessage() {
        Map<String, Object> m = base();
        List<Object> lines = new ArrayList<>();
        lines.add("<red>hi</red>");
        lines.add(null);
        m.put("signs", List.of(Map.of("at", List.of(0, 0, 0), "lines", lines)));
        assertEquals(List.of("<red>hi</red>", ""), parse(m).design().signs().getFirst().lines());
    }

    @Test
    void theShippedDefaultDesignParsesAndIsWhatThePaletteSays() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("houses/default.yml")) {
            assertNotNull(in, "houses/default.yml must be in the jar");
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        HouseDesignParser.Result r = HouseDesignParser.parse("default", YamlFiles.toMap(yaml));
        assertNotNull(r.design(), r.problems().toString());
        HouseDesign d = r.design();
        assertEquals(9, d.sizeX());
        assertEquals(7, d.sizeY());
        assertEquals(7, d.sizeZ());
        assertEquals(170, d.cells().stream().filter(c -> !c.blockData().equals("minecraft:air")).count(),
                "the generator header says 170 blocks (not counting air/keep)");
        assertNotNull(d.points().get("chimney"));
        assertEquals(1, d.signs().size());
        // every palette entry is used, none is missing (the parser already rejected unknown characters)
        for (Map.Entry<Character, String> e : d.palette().entrySet()) {
            assertTrue(e.getValue().startsWith("minecraft:"), e.toString());
        }
    }
}
