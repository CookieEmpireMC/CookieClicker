package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.plot.Plot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Undo data of one {@code /bakery plot fence place} (pure), stored as {@code fence-backups/<plot>/<createdAt>.yml}:
 * the old block data of every block the fence changed, and the plot's entrance before. Blocks with tile entities are
 * never replaced by the fence, so block data restores the world exactly.
 *
 * @param previousEntrance the plot's entrance before this fence (restored on undo), or null
 */
public record FenceBackup(String plotId, String worldKey, long createdAt, Plot.@Nullable Point previousEntrance,
                          List<HouseBackup.Cell> cells) {

    public FenceBackup {
        cells = List.copyOf(cells);
    }

    /** Puts every recorded block back. */
    public void restore(HouseBackup.BlockAccess world) {
        for (HouseBackup.Cell c : cells) {
            world.set(c.x(), c.y(), c.z(), c.oldData());
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plot", plotId);
        m.put("world", worldKey);
        m.put("created-at", createdAt);
        if (previousEntrance != null) {
            m.put("previous-entrance", List.of(previousEntrance.x(), previousEntrance.y(), previousEntrance.z()));
        }
        List<String> out = new ArrayList<>(cells.size());
        for (HouseBackup.Cell c : cells) {
            out.add(c.x() + " " + c.y() + " " + c.z() + " " + c.oldData());
        }
        m.put("cells", out);
        return m;
    }

    /** @throws IllegalArgumentException when the file is not a fence backup this class wrote */
    public static FenceBackup fromMap(Map<String, ?> m) {
        if (!(m.get("plot") instanceof String plot) || plot.isBlank()) {
            throw new IllegalArgumentException("plot missing");
        }
        if (!(m.get("world") instanceof String world) || world.isBlank()) {
            throw new IllegalArgumentException("world missing");
        }
        if (!(m.get("created-at") instanceof Number created)) {
            throw new IllegalArgumentException("created-at missing");
        }
        Plot.Point entrance = null;
        if (m.get("previous-entrance") instanceof List<?> l && l.size() == 3
                && l.get(0) instanceof Number x && l.get(1) instanceof Number y && l.get(2) instanceof Number z) {
            entrance = new Plot.Point(x.doubleValue(), y.doubleValue(), z.doubleValue());
        }
        if (!(m.get("cells") instanceof List<?> raw)) {
            throw new IllegalArgumentException("cells missing");
        }
        List<HouseBackup.Cell> cells = new ArrayList<>();
        for (Object o : raw) {
            String[] parts = String.valueOf(o).split(" ", 4);
            if (parts.length != 4) {
                throw new IllegalArgumentException("bad cell '" + o + "'");
            }
            try {
                cells.add(new HouseBackup.Cell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), parts[3]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad cell '" + o + "'", e);
            }
        }
        return new FenceBackup(plot, world, created.longValue(), entrance, cells);
    }
}
