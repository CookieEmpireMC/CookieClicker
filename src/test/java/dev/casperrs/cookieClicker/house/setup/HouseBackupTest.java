package dev.casperrs.cookieClicker.house.setup;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.casperrs.cookieClicker.plot.Plot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HouseBackupTest {

    /** A fake world: block data per position, "minecraft:air" when unset. */
    private static final class FakeWorld implements HouseBackup.BlockAccess {
        final Map<String, String> blocks = new HashMap<>();
        int sets;

        static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        @Override
        public String get(int x, int y, int z) {
            return blocks.getOrDefault(key(x, y, z), "minecraft:air");
        }

        @Override
        public void set(int x, int y, int z, String data) {
            sets++;
            blocks.put(key(x, y, z), data);
        }
    }

    private static HouseBackup capture(FakeWorld w, List<HouseBackup.Change> changes) {
        return HouseBackup.capture("main", "default", "minecraft:overworld", 1000L, new int[] {10, 64, 10},
                new int[] {3, 2, 3}, new Plot.Point(11.5, 66, 11.5), changes, w);
    }

    private static void apply(FakeWorld w, List<HouseBackup.Change> changes) {
        changes.forEach(c -> w.set(c.x(), c.y(), c.z(), c.newData()));
    }

    @Test
    void placeThenRestoreBringsBackExactlyTheOldWorldIncludingStates() {
        FakeWorld w = new FakeWorld();
        w.blocks.put("10,64,10", "minecraft:grass_block");
        w.blocks.put("11,64,10", "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]");
        w.blocks.put("12,65,12", "minecraft:chest[facing=east,type=single,waterlogged=false]");
        Map<String, String> before = new HashMap<>(w.blocks);

        List<HouseBackup.Change> changes = List.of(
                new HouseBackup.Change(10, 64, 10, "minecraft:stone"),
                new HouseBackup.Change(11, 64, 10, "minecraft:oak_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]"),
                new HouseBackup.Change(12, 65, 12, "minecraft:air"),
                new HouseBackup.Change(11, 65, 11, "minecraft:oak_planks"));
        HouseBackup backup = capture(w, changes);
        apply(w, changes);
        assertEquals("minecraft:stone", w.get(10, 64, 10));

        backup.restoreCells(w);
        // air in the fake world is "no entry": compare through get()
        for (String k : new String[] {"10,64,10", "11,64,10", "12,65,12", "11,65,11"}) {
            String[] p = k.split(",");
            String expected = before.getOrDefault(k, "minecraft:air");
            assertEquals(expected, w.get(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])), k);
        }
    }

    @Test
    void survivesAYamlRoundTripThroughTheRealFileFormat() throws Exception {
        FakeWorld w = new FakeWorld();
        w.blocks.put("10,64,10", "minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]");
        HouseBackup b = capture(w, List.of(new HouseBackup.Change(10, 64, 10, "minecraft:stone"),
                new HouseBackup.Change(11, 64, 10, "minecraft:dirt")));

        YamlConfiguration out = new YamlConfiguration();
        b.toMap().forEach(out::set);
        YamlConfiguration in = new YamlConfiguration();
        in.loadFromString(out.saveToString());
        HouseBackup back = HouseBackup.fromMap(dev.casperrs.cookieClicker.config.YamlFiles.toMap(in));

        assertEquals(b.plotId(), back.plotId());
        assertEquals(b.design(), back.design());
        assertEquals(b.worldKey(), back.worldKey());
        assertEquals(b.createdAt(), back.createdAt());
        assertArrayEquals(b.corner(), back.corner());
        assertArrayEquals(b.size(), back.size());
        assertEquals(b.previousChimney(), back.previousChimney());
        assertEquals(b.cells(), back.cells());

        FakeWorld after = new FakeWorld();
        after.blocks.put("10,64,10", "minecraft:stone");
        after.blocks.put("11,64,10", "minecraft:dirt");
        back.restoreCells(after);
        assertEquals("minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=false]", after.get(10, 64, 10));
        assertEquals("minecraft:air", after.get(11, 64, 10));
    }

    @Test
    void fromMapRejectsFilesThisClassDidNotWrite() {
        Map<String, Object> ok = new HouseBackup("p", "d", "minecraft:overworld", 5L, new int[3], new int[] {1, 1, 1}, null,
                List.of(new HouseBackup.Cell(0, 0, 0, "minecraft:stone"))).toMap();
        HouseBackup.fromMap(ok);

        for (String missing : new String[] {"plot", "design", "world", "created-at", "corner", "size"}) {
            Map<String, Object> m = new LinkedHashMap<>(ok);
            m.remove(missing);
            assertThrows(IllegalArgumentException.class, () -> HouseBackup.fromMap(m), missing);
        }
        Map<String, Object> badCell = new LinkedHashMap<>(ok);
        badCell.put("cells", new ArrayList<>(List.of("1 2")));
        assertThrows(IllegalArgumentException.class, () -> HouseBackup.fromMap(badCell));
        Map<String, Object> nanCell = new LinkedHashMap<>(ok);
        nanCell.put("cells", new ArrayList<>(List.of("a b c minecraft:stone")));
        assertThrows(IllegalArgumentException.class, () -> HouseBackup.fromMap(nanCell));
        Map<String, Object> badCorner = new LinkedHashMap<>(ok);
        badCorner.put("corner", List.of(1, 2));
        assertThrows(IllegalArgumentException.class, () -> HouseBackup.fromMap(badCorner));
    }

}
