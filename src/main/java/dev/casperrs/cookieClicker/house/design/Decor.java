package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The plot decoration set ({@code houses/decor.yml}, spec design/decor.md). Pure data and parser
 * over the YAML as plain maps:
 * <pre>
 * blocks:
 *   - {at: [3, 0, 5], block: "minecraft:barrel[facing=up,open=false]"}
 *   - {at: [3, 1, 5], block: "minecraft:lantern[hanging=false,waterlogged=false]"}
 * </pre>
 * {@code at} is a plot cell from the plot's min corner (the same frame as {@code layout:} in the house file), y 0 =
 * the first air layer above the ground; y -1 and -2 replace the plot's ground there (paths, beds, gravel edges; see
 * {@link DecorKeepOut} for where ground decor may go). A bad entry is a problem and is skipped; the rest still counts. Which cells
 * may hold decor at all is {@link DecorKeepOut}'s job; block data is checked against the server when placing. Blocks
 * keep file order, at most one per cell.
 */
public record Decor(List<Block> blocks) {

    /** Lowest decor layer: -2 (y -1 = the ground's top block, -2 the one below; 0 = the first air layer). */
    public static final int MIN_Y = -2;
    /** Highest decor layer (exclusive): props stand on the ground, they don't build towers. */
    public static final int MAX_Y = 16;
    public static final int MAX_BLOCKS = 4096;

    public Decor {
        blocks = List.copyOf(blocks);
    }

    /** One decor block in plot cells. */
    public record Block(int x, int y, int z, String blockData) {
    }

    /** The parsed set (never null; empty on a broken file) and every problem found. */
    public record Result(Decor decor, List<String> problems) {
        public Result {
            problems = List.copyOf(problems);
        }
    }

    public static Decor empty() {
        return new Decor(List.of());
    }

    public static Result parse(Map<String, ?> yaml) {
        List<String> problems = new ArrayList<>();
        Object raw = yaml.get("blocks");
        if (!(raw instanceof List<?> list)) {
            problems.add(raw == null ? "blocks missing" : "blocks must be a list");
            return new Result(empty(), problems);
        }
        List<Block> out = new ArrayList<>();
        Set<List<Integer>> seen = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String where = "blocks[" + i + "]";
            if (!(list.get(i) instanceof Map<?, ?> entry)) {
                problems.add(where + " must be {at: [x, y, z], block: \"...\"}");
                continue;
            }
            int[] at = cell(entry.get("at"));
            if (at == null) {
                problems.add(where + ".at must be [x, y, z] whole numbers");
                continue;
            }
            if (at[0] < 0 || at[2] < 0 || at[0] >= HouseLayout.MAX_SIZE || at[2] >= HouseLayout.MAX_SIZE) {
                problems.add(where + ".at " + at[0] + " " + at[1] + " " + at[2] + " is outside the plot");
                continue;
            }
            if (at[1] < MIN_Y || at[1] >= MAX_Y) {
                problems.add(where + ".at y " + at[1] + " must be " + MIN_Y + " to " + (MAX_Y - 1));
                continue;
            }
            if (!(entry.get("block") instanceof String block) || block.isBlank()) {
                problems.add(where + ".block missing");
                continue;
            }
            if (!seen.add(List.of(at[0], at[1], at[2]))) {
                problems.add(where + ": cell " + at[0] + " " + at[1] + " " + at[2] + " is used twice; skipped");
                continue;
            }
            if (out.size() >= MAX_BLOCKS) {
                problems.add("more than " + MAX_BLOCKS + " blocks; the rest is skipped");
                break;
            }
            out.add(new Block(at[0], at[1], at[2], block.trim()));
        }
        return new Result(new Decor(out), problems);
    }

    /** Every distinct block data string the set uses. */
    public Set<String> allBlockData() {
        Set<String> out = new HashSet<>();
        blocks.forEach(b -> out.add(b.blockData()));
        return out;
    }

    private static int @Nullable [] cell(@Nullable Object raw) {
        if (!(raw instanceof List<?> l) || l.size() != 3) {
            return null;
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            if (!(l.get(i) instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
                return null;
            }
            out[i] = n.intValue();
        }
        return out;
    }
}
