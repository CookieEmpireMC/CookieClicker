package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Undo data of one {@code /bakery plot setup} (pure), stored as {@code setup-backups/<plot>/<createdAt>.yml} next to a
 * structure file ({@code .nbt}) of the house box (tile contents included). {@link #cells} holds the old block data of
 * every block the house and the fence changed; {@link #previousPlot} is the plots.yml entry before the setup.
 */
public record SetupBackup(String plotId, String design, String worldKey, long createdAt, int[] houseCorner,
                          int[] houseSize, List<HouseBackup.Cell> cells, Plot previousPlot) {

    public SetupBackup {
        houseCorner = houseCorner.clone();
        houseSize = houseSize.clone();
        cells = List.copyOf(cells);
    }

    /** Puts every recorded block back (block data; tile contents come from the structure file). */
    public void restoreCells(HouseBackup.BlockAccess world) {
        for (HouseBackup.Cell c : cells) {
            world.set(c.x(), c.y(), c.z(), c.oldData());
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plot", plotId);
        m.put("design", design);
        m.put("world", worldKey);
        m.put("created-at", createdAt);
        m.put("house-corner", List.of(houseCorner[0], houseCorner[1], houseCorner[2]));
        m.put("house-size", List.of(houseSize[0], houseSize[1], houseSize[2]));
        m.put("previous-plot", PlotCodec.toMap(List.of(previousPlot)));
        List<String> out = new ArrayList<>(cells.size());
        for (HouseBackup.Cell c : cells) {
            out.add(c.x() + " " + c.y() + " " + c.z() + " " + c.oldData());
        }
        m.put("cells", out);
        return m;
    }

    /** @throws IllegalArgumentException when the file is not a setup backup this class wrote */
    @SuppressWarnings("unchecked")
    public static SetupBackup fromMap(Map<String, ?> m) {
        String plot = string(m, "plot");
        String design = string(m, "design");
        String world = string(m, "world");
        if (!(m.get("created-at") instanceof Number created)) {
            throw new IllegalArgumentException("created-at missing");
        }
        int[] corner = ints(m.get("house-corner"), "house-corner");
        int[] size = ints(m.get("house-size"), "house-size");
        if (!(m.get("previous-plot") instanceof Map<?, ?> prev)) {
            throw new IllegalArgumentException("previous-plot missing");
        }
        List<String> problems = new ArrayList<>();
        List<Plot> plots = PlotCodec.fromMap((Map<String, ?>) prev, problems::add);
        if (plots.size() != 1 || !plots.getFirst().id().equals(plot)) {
            throw new IllegalArgumentException("previous-plot is not plot " + plot + " " + problems);
        }
        List<HouseBackup.Cell> cells = new ArrayList<>();
        if (m.get("cells") instanceof List<?> raw) {
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
        }
        return new SetupBackup(plot, design, world, created.longValue(), corner, size, cells, plots.getFirst());
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
