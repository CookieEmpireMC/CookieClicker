package dev.casperrs.cookieClicker.house;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.casperrs.cookieClicker.config.YamlFiles;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/**
 * Decor keep-out columns of the shipped factory layout (brief plot-polish: the fence, factory, walk/pedestal/panels
 * and belt are never decorated; the parser rejects those cells at setup).
 */
class DecorKeepOutTest {

    private static HouseDesign factory() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (InputStream in = DecorKeepOutTest.class.getResourceAsStream("/houses/factory.yml")) {
            assumeTrue(in != null, "houses/factory.yml not on the classpath");
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        HouseDesignParser.Result r = HouseDesignParser.parse("factory", YamlFiles.toMap(y));
        assertNotNull(r.design(), r.problems().toString());
        return r.design();
    }

    @Test
    void eachZoneOfTheFactoryPlotIsKeptOutAndOpenGroundIsNot() throws Exception {
        DecorKeepOut k = DecorKeepOut.of(factory());
        assertNotNull(k);
        assertEquals("the fence ring", k.reason(0, 15));
        assertEquals("the fence ring", k.reason(15, 29));
        assertEquals("the factory", k.reason(10, 15));
        assertEquals("the walk, pedestal and panels", k.reason(13, 3));
        assertEquals("outside the plot", k.reason(-1, 5));
        assertNull(k.reason(2, 2));
    }

    @Test
    void allowedDropsOnlyTheRejectedCells() throws Exception {
        DecorKeepOut k = DecorKeepOut.of(factory());
        assertNotNull(k);
        Decor.Block free = new Decor.Block(2, 0, 2, "minecraft:barrel");
        Decor d = new Decor(List.of(new Decor.Block(0, 0, 15, "minecraft:barrel"), free,
                new Decor.Block(10, 0, 15, "minecraft:barrel")));
        List<String> problems = new ArrayList<>();
        assertEquals(List.of(free), k.allowed(d, problems));
        assertEquals(2, problems.size(), problems.toString());
    }

    @Test
    void theShippedDecorFileAvoidsEveryKeepOutCell() throws Exception {
        Path file = Path.of("src/main/resources/houses/decor.yml");
        assumeTrue(Files.isRegularFile(file), "houses/decor.yml is not on this branch yet");
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(file));
        Decor decor = Decor.parse(YamlFiles.toMap(y)).decor();
        List<String> problems = new ArrayList<>();
        int kept = DecorKeepOut.of(factory()).allowed(decor, problems).size();
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(decor.blocks().size(), kept);
    }
}
