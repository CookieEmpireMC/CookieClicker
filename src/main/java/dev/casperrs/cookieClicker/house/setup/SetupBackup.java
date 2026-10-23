package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Undo data of one {@code /bakery plot setup} (pure), stored next to a structure file of the house box.
 * {@link #cells} holds the old block data of every block the setup changed; {@link #previousPlot} is the plots.yml
 * entry before it.
 */
public record SetupBackup(String plotId, String design, String worldKey, long createdAt, int[] houseCorner,
                          int[] houseSize, List<HouseBackup.Cell> cells, Plot previousPlot) {

    public SetupBackup {
        houseCorner = houseCorner.clone();
        houseSize = houseSize.clone();
        cells = List.copyOf(cells);
    }

    public void restoreCells(HouseBackup.BlockAccess world) {
        HouseBackup.restore(cells, world);
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
        m.put("cells", HouseBackup.encode(cells));
        return m;
    }

    /** @throws IllegalArgumentException when the file is not a setup backup this class wrote */
    @SuppressWarnings("unchecked")
    public static SetupBackup fromMap(Map<String, ?> m) {
        String plot = HouseBackup.string(m, "plot");
        String design = HouseBackup.string(m, "design");
        String world = HouseBackup.string(m, "world");
        if (!(m.get("created-at") instanceof Number created)) {
            throw new IllegalArgumentException("created-at missing");
        }
        int[] corner = HouseBackup.ints(m.get("house-corner"), "house-corner");
        int[] size = HouseBackup.ints(m.get("house-size"), "house-size");
        if (!(m.get("previous-plot") instanceof Map<?, ?> prev)) {
            throw new IllegalArgumentException("previous-plot missing");
        }
        List<String> problems = new ArrayList<>();
        List<Plot> plots = PlotCodec.fromMap((Map<String, ?>) prev, problems::add);
        if (plots.size() != 1 || !plots.getFirst().id().equals(plot)) {
            throw new IllegalArgumentException("previous-plot is not plot " + plot + " " + problems);
        }
        return new SetupBackup(plot, design, world, created.longValue(), corner, size,
                HouseBackup.decode(m.get("cells")), plots.getFirst());
    }
}
