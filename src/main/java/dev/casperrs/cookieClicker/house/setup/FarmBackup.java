package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.farm.Farm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Undo data of one {@code /bakery farm setup} (pure): the pasted box cut into chunk-column slabs, each saved as a
 * structure file {@code <index>.nbt} next to this manifest. Undo places every slab back, which restores blocks and
 * tile contents exactly.
 */
public record FarmBackup(String farmId, String worldKey, long createdAt, Farm.Box box, List<Farm.Box> slabs) {

    public FarmBackup {
        slabs = List.copyOf(slabs);
    }

    /** The box cut along chunk borders (16 x 16 columns, full height), in x then z order. */
    public static List<Farm.Box> slabs(Farm.Box box) {
        return slabs(box, Long.MAX_VALUE);
    }

    /**
     * The box cut along chunk borders, each column also cut into y ranges so that one slab holds at most
     * {@code maxBlocks} blocks (at least one layer); x, then z, then y order.
     */
    public static List<Farm.Box> slabs(Farm.Box box, long maxBlocks) {
        List<Farm.Box> out = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x = (x & ~15) + 16) {
            int toX = Math.min(box.maxX(), (x & ~15) + 15);
            for (int z = box.minZ(); z <= box.maxZ(); z = (z & ~15) + 16) {
                int toZ = Math.min(box.maxZ(), (z & ~15) + 15);
                long layer = (long) (toX - x + 1) * (toZ - z + 1);
                int height = (int) Math.max(1, Math.min(box.maxY() - box.minY() + 1L, maxBlocks / layer));
                for (int y = box.minY(); y <= box.maxY(); y += height) {
                    out.add(new Farm.Box(x, y, z, toX, Math.min(box.maxY(), y + height - 1), toZ));
                }
            }
        }
        return out;
    }

    public static long volume(Farm.Box b) {
        int[] s = b.size();
        return (long) s[0] * s[1] * s[2];
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("farm", farmId);
        m.put("world", worldKey);
        m.put("created-at", createdAt);
        m.put("box", box(box));
        List<Object> list = new ArrayList<>();
        slabs.forEach(s -> list.add(box(s)));
        m.put("slabs", list);
        return m;
    }

    /** @throws IllegalArgumentException when the file is not a backup this class wrote */
    public static FarmBackup fromMap(Map<String, ?> m) {
        if (!(m.get("created-at") instanceof Number created)) {
            throw new IllegalArgumentException("created-at missing");
        }
        if (!(m.get("slabs") instanceof List<?> raw) || raw.isEmpty()) {
            throw new IllegalArgumentException("slabs missing");
        }
        List<Farm.Box> slabs = new ArrayList<>();
        raw.forEach(o -> slabs.add(box(o)));
        return new FarmBackup(HouseBackup.string(m, "farm"), HouseBackup.string(m, "world"), created.longValue(),
                box(m.get("box")), slabs);
    }

    private static Map<String, Object> box(Farm.Box b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("min", List.of(b.minX(), b.minY(), b.minZ()));
        m.put("max", List.of(b.maxX(), b.maxY(), b.maxZ()));
        return m;
    }

    private static Farm.Box box(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("box must be {min, max}");
        }
        int[] min = HouseBackup.ints(m.get("min"), "min");
        int[] max = HouseBackup.ints(m.get("max"), "max");
        return new Farm.Box(min[0], min[1], min[2], max[0], max[1], max[2]);
    }
}
