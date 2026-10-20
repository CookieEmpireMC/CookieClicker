package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.plot.Plot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Undo data of one {@code /bakery house place} (pure). Stored as {@code house-backups/<plot>/<createdAt>.yml}, next to
 * a structure file ({@code .nbt}) of the same box that also holds tile-entity contents (chests, signs). Undo places
 * the structure when it loads; the {@link #cells} (old block data of every block the paste changed) are the fallback
 * and what the round-trip tests check.
 *
 * @param corner          minimum corner of the pasted box (world block coordinates)
 * @param size            size of that box
 * @param previousChimney the plot's first smoke point before the paste, or null
 * @param previousSmoke   every smoke point of the plot before the paste (restored on undo)
 * @param previousSteam   every steam point of the plot before the paste (restored on undo)
 */
public record HouseBackup(String plotId, String design, String worldKey, long createdAt, int[] corner, int[] size,
                          Plot.@Nullable Point previousChimney, List<Cell> cells, List<Plot.Point> previousSmoke,
                          List<Plot.Point> previousSteam) {

    public HouseBackup {
        cells = List.copyOf(cells);
        previousSmoke = List.copyOf(previousSmoke);
        previousSteam = List.copyOf(previousSteam);
    }

    /** A backup with at most one previous smoke point and no steam. */
    public HouseBackup(String plotId, String design, String worldKey, long createdAt, int[] corner, int[] size,
                       Plot.@Nullable Point previousChimney, List<Cell> cells) {
        this(plotId, design, worldKey, createdAt, corner, size, previousChimney, cells,
                previousChimney == null ? List.of() : List.of(previousChimney), List.of());
    }

    /** One changed block and the block data it had before the paste. */
    public record Cell(int x, int y, int z, String oldData) {
    }

    /** World access for capture/restore, so the logic runs in tests without a server. */
    public interface BlockAccess {
        String get(int x, int y, int z);

        void set(int x, int y, int z, String blockData);
    }

    /** A block the paste will set: world position and new block data. */
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

    /** As above, remembering every smoke and steam point the plot had before the paste. */
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

    /** Puts every recorded block back (block data only; tile contents come from the structure file). */
    public void restoreCells(BlockAccess world) {
        for (Cell c : cells) {
            world.set(c.x(), c.y(), c.z(), c.oldData());
        }
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
        List<String> out = new ArrayList<>(cells.size());
        for (Cell c : cells) {
            out.add(c.x() + " " + c.y() + " " + c.z() + " " + c.oldData());
        }
        m.put("cells", out);
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
        List<Cell> cells = new ArrayList<>();
        if (m.get("cells") instanceof List<?> raw) {
            for (Object o : raw) {
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
        }
        return new HouseBackup(plot, design, world, created.longValue(), corner, size, chimney, cells, smoke, steam);
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

    private static String string(Map<String, ?> m, String key) {
        if (!(m.get(key) instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(key + " missing");
        }
        return s;
    }

    private static int[] ints(Object raw, String key) {
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
