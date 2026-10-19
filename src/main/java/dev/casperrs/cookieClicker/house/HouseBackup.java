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
 * @param previousChimney the plot's chimney point before the paste (restored on undo), or null
 */
public record HouseBackup(String plotId, String design, String worldKey, long createdAt, int[] corner, int[] size,
                          Plot.@Nullable Point previousChimney, List<Cell> cells) {

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
        List<Cell> cells = new ArrayList<>();
        for (Change c : changes) {
            String old = world.get(c.x(), c.y(), c.z());
            if (!old.equals(c.newData())) {
                cells.add(new Cell(c.x(), c.y(), c.z(), old));
            }
        }
        return new HouseBackup(plotId, design, worldKey, createdAt, corner.clone(), size.clone(), previousChimney,
                List.copyOf(cells));
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
        Plot.Point chimney = null;
        if (m.get("previous-chimney") instanceof List<?> l && l.size() == 3
                && l.get(0) instanceof Number x && l.get(1) instanceof Number y && l.get(2) instanceof Number z) {
            chimney = new Plot.Point(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
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
        return new HouseBackup(plot, design, world, created.longValue(), corner, size, chimney, List.copyOf(cells));
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
