package dev.casperrs.cookieClicker.house.setup;

import dev.casperrs.cookieClicker.belt.BeltPath;
import dev.casperrs.cookieClicker.house.design.BeltStyle;
import dev.casperrs.cookieClicker.house.design.DesignFiles;
import dev.casperrs.cookieClicker.house.design.HouseLayout;
import dev.casperrs.cookieClicker.plot.Plot;
import org.bukkit.block.data.BlockData;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The conveyor belt of a setup: its cells along {@code layout.belt} and the frame blocks of {@code houses/belt.yml}
 * under them. A missing or broken style is a warning and means "no frame"; the belt surface still runs.
 */
final class BeltFramePlanner {

    private static final String FILE = DesignFiles.DIR + "/" + DesignFiles.BELT_STYLE;

    private BeltFramePlanner() {
    }

    /** The plot's belt (null without one in the layout), its frame added to {@code changes}. */
    static Plot.@Nullable Belt plan(Logger log, HouseLayout layout, int ax, int y0, int az, @Nullable String styleText,
                                    Map<String, BlockData> known, ChangeList changes) {
        int[] f = layout.beltFrom();
        int[] t = layout.beltTo();
        if (f == null || t == null) {
            return null;
        }
        List<Plot.Cell> cells = BeltPath.cells(new int[] {ax + f[0], y0 + f[1], az + f[2]},
                new int[] {ax + t[0], y0 + t[1], az + t[2]});
        if (cells == null) {
            return null;
        }
        BeltStyle style = style(log, styleText, known);
        for (int i = 0; i < cells.size(); i++) {
            Plot.Cell c = cells.get(i);
            List<String> column = style.column(i, cells.size());
            for (int k = 0; k < column.size(); k++) {
                changes.setUnlessTile(c.x(), c.y() + k, c.z(), known.get(column.get(k)).getAsString());
            }
        }
        return new Plot.Belt(cells, style.surface());
    }

    private static BeltStyle style(Logger log, @Nullable String text, Map<String, BlockData> known) {
        if (text == null) {
            log.warning(FILE + " not found; the belt gets no frame blocks");
            return BeltStyle.none();
        }
        BeltStyle.Result r = DesignFiles.parse(text, BeltStyle::parse, problem -> new BeltStyle.Result(null,
                List.of(problem)));
        if (r.style() == null) {
            log.warning(FILE + ": " + String.join("; ", r.problems()) + "; the belt gets no frame blocks");
            return BeltStyle.none();
        }
        for (String s : r.style().allBlockData()) {
            try {
                known.computeIfAbsent(s, DesignBlocks::create);
            } catch (IllegalArgumentException e) {
                log.warning(FILE + ": '" + s + "' is not valid block data; the belt gets no frame blocks");
                return BeltStyle.none(r.style().surfaceHeight());
            }
        }
        return r.style();
    }
}
