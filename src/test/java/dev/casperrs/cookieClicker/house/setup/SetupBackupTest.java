package dev.casperrs.cookieClicker.house.setup;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.plot.Plot;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SetupBackupTest {

    private static Plot previous() {
        return new Plot("main", "minecraft:overworld", "bakery_plot", null,
                new Plot.Viewpoint(1.5, 64, 2.5, 90f, 10f), new Plot.Pedestal(3.5, 64, 4.5, -90f),
                List.of(new Plot.Point(5.5, 70, 6.5), new Plot.Point(7.5, 71, 8.5)), List.of(new Plot.Point(9.5, 72, 1.5)),
                new Plot.Point(0.5, 64, 0.5), new Plot.Pedestal(2.5, 64, 4.5, 0f), new Plot.Box(1, 64, 2, 10, 80, 20));
    }

    private static SetupBackup backup() {
        return new SetupBackup("main", "factory", "minecraft:overworld", 1760000000000L, new int[] {10, 65, -4},
                new int[] {19, 17, 14},
                List.of(new HouseBackup.Cell(10, 65, -4, "minecraft:grass_block"),
                        new HouseBackup.Cell(-3, 66, 2, "minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=true]"),
                        new HouseBackup.Cell(0, 0, 0, "minecraft:air")), previous());
    }

    /** Through a real YAML text, like the file on disk. */
    private static SetupBackup viaYaml(SetupBackup b) throws Exception {
        YamlConfiguration out = new YamlConfiguration();
        b.toMap().forEach(out::set);
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());
        return SetupBackup.fromMap(YamlFiles.toMap(in));
    }

    @Test
    void roundTripsThroughYamlText() throws Exception {
        SetupBackup b = backup();
        SetupBackup back = viaYaml(b);
        assertEquals(b.plotId(), back.plotId());
        assertEquals(b.design(), back.design());
        assertEquals(b.worldKey(), back.worldKey());
        assertEquals(b.createdAt(), back.createdAt());
        assertArrayEquals(b.houseCorner(), back.houseCorner());
        assertArrayEquals(b.houseSize(), back.houseSize());
        assertEquals(b.cells(), back.cells(), "block data with commas and = survives (split into 4 parts)");
        assertEquals(b.previousPlot(), back.previousPlot(), "smoke, steam, entrance, shop and house box included");
    }

    @Test
    void restoreCellsPutsEveryOldBlockBack() {
        Map<String, String> world = new HashMap<>();
        SetupBackup b = backup();
        for (HouseBackup.Cell c : b.cells()) {
            world.put(c.x() + "," + c.y() + "," + c.z(), "minecraft:stone_bricks");
        }
        b.restoreCells(new HouseBackup.BlockAccess() {
            @Override
            public String get(int x, int y, int z) {
                return world.get(x + "," + y + "," + z);
            }

            @Override
            public void set(int x, int y, int z, String blockData) {
                world.put(x + "," + y + "," + z, blockData);
            }
        });
        for (HouseBackup.Cell c : b.cells()) {
            assertEquals(c.oldData(), world.get(c.x() + "," + c.y() + "," + c.z()));
        }
    }

    @Test
    void corruptFilesAreRejectedWithAnException() throws Exception {
        for (String key : List.of("plot", "design", "world", "created-at", "house-corner", "house-size",
                "previous-plot")) {
            Map<String, Object> m = new LinkedHashMap<>(backup().toMap());
            m.remove(key);
            assertThrows(IllegalArgumentException.class, () -> SetupBackup.fromMap(m), key);
        }
        for (Map.Entry<String, Object> bad : List.of(Map.entry("cells", (Object) List.of("1 2 3")),
                Map.entry("cells", (Object) List.of("a b c minecraft:air")),
                Map.entry("house-corner", (Object) List.of(1, 2)))) {
            Map<String, Object> m = new LinkedHashMap<>(backup().toMap());
            m.put(bad.getKey(), bad.getValue());
            assertThrows(IllegalArgumentException.class, () -> SetupBackup.fromMap(m), bad.toString());
        }
    }

}
