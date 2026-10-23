package dev.casperrs.cookieClicker.house.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.casperrs.cookieClicker.config.VisualFallbacks;
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
 * Decor keep-out columns of the shipped factory layout (the fence, factory, walk/pedestal/panels
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

    @Test
    void shippedSlotsWithTheirFootprintsAreClearOfEveryLayoutZoneAndTheShippedDecor() throws Exception {
        Path slotsFile = Path.of("src/main/resources/houses/building-slots.yml");
        Path decorFile = Path.of("src/main/resources/houses/decor.yml");
        assumeTrue(Files.isRegularFile(slotsFile) && Files.isRegularFile(decorFile), "roster files not present");
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(slotsFile));
        BuildingSlots slots = BuildingSlots.parse(YamlFiles.toMap(y)).slots();
        java.util.Map<String, Double> footprints = VisualFallbacks.FOOTPRINTS;

        DecorKeepOut layoutOnly = DecorKeepOut.of(factory());
        DecorKeepOut withSlots = DecorKeepOut.of(factory(), slots, footprints);
        assertNotNull(layoutOnly);
        assertNotNull(withSlots);
        // every cell any footprint covers: inside the plot, off the fence, factory, walk, panels, basket and belt
        List<String> bad = new ArrayList<>();
        slots.slots().forEach((id, list) -> list.forEach(sl -> {
            double h = footprints.getOrDefault(id, 0.0) / 2;
            int x0 = (int) Math.floor(sl.x() + 0.5 - h);
            int x1 = (int) Math.ceil(sl.x() + 0.5 + h) - 1;
            int z0 = (int) Math.floor(sl.z() + 0.5 - h);
            int z1 = (int) Math.ceil(sl.z() + 0.5 + h) - 1;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    String why = layoutOnly.reason(x, z);
                    if (why != null) {
                        bad.add(id + " slot " + sl.x() + "," + sl.z() + " covers " + x + "," + z + " (" + why + ")");
                    }
                }
            }
        }));
        assertTrue(bad.isEmpty(), bad.toString());

        y = new YamlConfiguration();
        y.loadFromString(Files.readString(decorFile));
        Decor decor = Decor.parse(YamlFiles.toMap(y)).decor();
        List<String> problems = new ArrayList<>();
        assertEquals(decor.blocks().size(), withSlots.allowed(decor, problems).size(), problems.toString());
    }
}
