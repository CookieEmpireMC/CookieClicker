package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.plot.Plot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Undo data of one {@code /bakery house place} (pure), stored next to a structure file of the same box that also
 * holds tile contents. Undo places the structure when it loads; {@link #cells} (old block data of every changed
 * block) is the fallback.
 *
 * @param corner          minimum corner of the pasted box
 * @param previousChimney the plot's first smoke point before the paste, or null
 */
public record HouseBackup(String plotId, String design, String worldKey, long createdAt, int[] corner, int[] size,
                          Plot.@Nullable Point previousChimney, List<Cell> cells, List<Plot.Point> previousSmoke,
                          List<Plot.Point> previousSteam) {

    public HouseBackup {
        cells = List.copyOf(cells);
        previousSmoke = List.copyOf(previousSmoke);
        previousSteam = List.copyOf(previousSteam);
    }

    public HouseBackup(String plotId, String design, String worldKey, long createdAt, int[] corner, int[] size,
                       Plot.@Nullable Point previousChimney, List<Cell> cells) {
        this(plotId, design, worldKey, createdAt, corner, size, previousChimney, cells,
                previousChimney == null ? List.of() : List.of(previousChimney), List.of());
    }

    /** One changed block and its block data before the change. */
    public record Cell(int x, int y, int z, String oldData) {
    }

    /** World access for capture/restore, so the logic runs in tests without a server. */
    public interface BlockAccess {
        String get(int x, int y, int z);

        void set(int x, int y, int z, String blockData);
    }

    public record Change(int x, int y, int z, String newData) {
    }

    /**
     * Records the current data of every block {@code changes} would alter (identical blocks are skipped), before the
     * paste. Returns the backup; the caller then applies the changes.
     */
    public static HouseBackup capture(String plotId, String design, String worldKey, long createdAt, int[] corner,
                                      int[] size, Plot.@Nullable Point previousChimney, List<Change> changes,
                                      BlockAccess world) {
        return capture(plotId, design, worldKey, createdAt, corner, size,
                previousChimney == null ? List.of() : List.of(previousChimney), List.of(), changes, world);
    }

    public static HouseBackup capture(String plotId, String design, String worldKey, long createdAt, int[] corner,
                                      int[] size, List<Plot.Point> previousSmoke, List<Plot.Point> previousSteam,
                                      List<Change> changes, BlockAccess world) {
        List<Cell> cells = new ArrayList<>();
        for (Change c : changes) {
            String old = world.get(c.x(), c.y(), c.z());
            if (!old.equals(c.newData())) {
                cells.add(new Cell(c.x(), c.y(), c.z(), old));
            }
        }
        return new HouseBackup(plotId, design, worldKey, createdAt, corner.clone(), size.clone(),
                previousSmoke.isEmpty() ? null : previousSmoke.getFirst(), cells, previousSmoke, previousSteam);
    }

    public void restoreCells(BlockAccess world) {
        restore(cells, world);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plot", plotId);
        m.put("design", design);
        m.put("world", worldKey);
        m.put("created-at", createdAt);
        m.put("corner", List.of(corner[0], corner[1], corner[2]));
        m.put("size", List.of(size[0], size[1], size[2]));
        if (previousChimney != null) {
            m.put("previous-chimney", List.of(previousChimney.x(), previousChimney.y(), previousChimney.z()));
        }
        if (previousSmoke.size() > 1) {
            m.put("previous-smoke", triples(previousSmoke));
        }
        if (!previousSteam.isEmpty()) {
            m.put("previous-steam", triples(previousSteam));
        }
        m.put("cells", encode(cells));
        return m;
    }

    /** @throws IllegalArgumentException when the file is not a backup this class wrote */
    public static HouseBackup fromMap(Map<String, ?> m) {
        String plot = string(m, "plot");
        String design = string(m, "design");
        String world = string(m, "world");
        if (!(m.get("created-at") instanceof Number created)) {
            throw new IllegalArgumentException("created-at missing");
        }
        int[] corner = ints(m.get("corner"), "corner");
        int[] size = ints(m.get("size"), "size");
        Plot.Point chimney = triple(m.get("previous-chimney"));
        List<Plot.Point> smoke = m.get("previous-smoke") instanceof List<?> rawSmoke ? readTriples(rawSmoke)
                : chimney == null ? List.of() : List.of(chimney);
        List<Plot.Point> steam = m.get("previous-steam") instanceof List<?> rawSteam ? readTriples(rawSteam) : List.of();
        return new HouseBackup(plot, design, world, created.longValue(), corner, size, chimney, decode(m.get("cells")),
                smoke, steam);
    }

    private static List<List<Double>> triples(List<Plot.Point> points) {
        List<List<Double>> out = new ArrayList<>();
        for (Plot.Point p : points) {
            out.add(List.of(p.x(), p.y(), p.z()));
        }
        return out;
    }

    private static List<Plot.Point> readTriples(List<?> raw) {
        List<Plot.Point> out = new ArrayList<>();
        for (Object o : raw) {
            Plot.Point p = triple(o);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private static Plot.@Nullable Point triple(@Nullable Object raw) {
        if (raw instanceof List<?> l && l.size() == 3
                && l.get(0) instanceof Number x && l.get(1) instanceof Number y && l.get(2) instanceof Number z) {
            return new Plot.Point(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        return null;
    }

    static void restore(List<Cell> cells, BlockAccess world) {
        for (Cell c : cells) {
            world.set(c.x(), c.y(), c.z(), c.oldData());
        }
    }

    static List<String> encode(List<Cell> cells) {
        List<String> out = new ArrayList<>(cells.size());
        for (Cell c : cells) {
            out.add(c.x() + " " + c.y() + " " + c.z() + " " + c.oldData());
        }
        return out;
    }

    static List<Cell> decode(@Nullable Object raw) {
        List<Cell> cells = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return cells;
        }
        for (Object o : list) {
            String[] parts = String.valueOf(o).split(" ", 4);
            if (parts.length != 4) {
                throw new IllegalArgumentException("bad cell '" + o + "'");
            }
            try {
                cells.add(new Cell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), parts[3]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad cell '" + o + "'", e);
            }
        }
        return cells;
    }

    static String string(Map<String, ?> m, String key) {
        if (!(m.get(key) instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(key + " missing");
        }
        return s;
    }

    static int[] ints(@Nullable Object raw, String key) {
        if (!(raw instanceof List<?> l) || l.size() != 3) {
            throw new IllegalArgumentException(key + " must be [x, y, z]");
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!(l.get(i) instanceof Number n)) {
                throw new IllegalArgumentException(key + " must be [x, y, z]");
            }
            out[i] = n.intValue();
        }
        return out;
    }
}
