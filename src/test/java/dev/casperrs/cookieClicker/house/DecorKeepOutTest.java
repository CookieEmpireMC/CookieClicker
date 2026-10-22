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

    @Test
    void aBuildingSlotIsKeptOutOnItsCellOnly() throws Exception {
        BuildingSlots slots = new BuildingSlots(java.util.Map.of("villager_baker",
                List.of(new BuildingSlots.Slot(2, 0, 2, 180f))));
        DecorKeepOut k = DecorKeepOut.of(factory(), slots);
        assertNotNull(k);
        assertEquals("a building slot", k.reason(2, 2));
        assertNull(k.reason(3, 2));
        assertNull(DecorKeepOut.of(factory()).reason(2, 2), "the layout alone leaves that cell open");
    }

    @Test
    void aFootprintKeepsOutEveryCellItsSquareCoversAndNoMore() throws Exception {
        BuildingSlots slots = new BuildingSlots(java.util.Map.of(
                "industrial_oven", List.of(new BuildingSlots.Slot(4, 0, 4, 90f)),
                "villager_baker", List.of(new BuildingSlots.Slot(4, 0, 7, 0f))));
        DecorKeepOut k = DecorKeepOut.of(factory(), slots,
                java.util.Map.of("industrial_oven", 1.55, "villager_baker", 0.82));
        assertNotNull(k);
        // 1.55 around the middle of cell 4 (4.5 +- 0.775) reaches cells 3..5 on both axes
        assertEquals("a building slot", k.reason(3, 3));
        assertEquals("a building slot", k.reason(5, 5));
        assertNull(k.reason(6, 4));
        assertNull(k.reason(2, 4));
        // 0.82 stays inside its own cell; a building without a footprint entry also keeps just the cell
        assertEquals("a building slot", k.reason(4, 7));
        assertNull(k.reason(3, 7));
        assertNull(k.reason(5, 7));
    }

    @Test
    void shippedSlotsWithTheirFootprintsAreClearOfEveryLayoutZoneAndTheShippedDecor() throws Exception {
        Path slotsFile = Path.of("src/main/resources/houses/building-slots.yml");
        Path decorFile = Path.of("src/main/resources/houses/decor.yml");
        Path configFile = Path.of("src/main/resources/config.yml");
        assumeTrue(Files.isRegularFile(slotsFile) && Files.isRegularFile(decorFile), "roster files not present");
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(Files.readString(slotsFile));
        BuildingSlots slots = BuildingSlots.parse(YamlFiles.toMap(y)).slots();
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(configFile.toFile());
        java.util.Map<String, Double> footprints = new java.util.HashMap<>();
        var section = cfg.getConfigurationSection("plot.buildings.footprints");
        assertNotNull(section);
        section.getKeys(false).forEach(id -> footprints.put(id, section.getDouble(id)));

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
