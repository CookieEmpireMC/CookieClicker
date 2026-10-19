package dev.casperrs.cookieClicker.house;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a house design file (brief starter-house, Contract "house design"). Pure Java over the YAML as plain maps:
 * <pre>
 * size: [9, 7, 7]            # x (row length), y (number of layers), z (rows per layer)
 * origin: [0, 0, 0]          # optional: the design cell placed on the house region's minimum corner
 * palette:                   # one character -> block data
 *   "P": "minecraft:oak_planks"
 *   "_": "minecraft:air"     # to clear a cell, map a character to air
 * layers:                    # bottom -> top; each layer is a list of rows (north -> south),
 *   - ["PPPPPPPPP", ...]     # each row a string of characters (west -> east)
 * points:
 *   chimney: [7, 7, 1]       # the cell the smoke starts in (smoke plays at its bottom centre)
 *   door: [4, 1, 6]          # optional; every point must be inside size
 * signs:                     # optional: plain text set on the sign's front side after pasting
 *   - at: [5, 2, 0]
 *     lines: ["", "Bakery", "", ""]
 * </pre>
 * {@code .} and space mean "keep the world's block" (no change, no backup entry). Spec: design/house-default.md.
 * Every problem is collected; the design is returned only when there were none.
 */
public final class HouseDesignParser {

    public static final char KEEP = '.';
    /** Per axis; a typo in {@code size} must not allocate a huge grid. */
    public static final int MAX_SIZE = 256;

    /** The design, or null with at least one problem. */
    public record Result(@Nullable HouseDesign design, List<String> problems) {
    }

    private HouseDesignParser() {
    }

    public static boolean isKeep(char c) {
        return c == KEEP || c == ' ';
    }

    public static Result parse(String name, Map<String, ?> yaml) {
        List<String> problems = new ArrayList<>();
        int[] size = ints(yaml.get("size"), 3);
        if (size == null || size[0] < 1 || size[1] < 1 || size[2] < 1
                || size[0] > MAX_SIZE || size[1] > MAX_SIZE || size[2] > MAX_SIZE) {
            problems.add("size must be [x, y, z] with whole numbers from 1 to " + MAX_SIZE);
            return new Result(null, problems);
        }
        int[] origin = yaml.get("origin") == null ? new int[3] : ints(yaml.get("origin"), 3);
        if (origin == null || !inside(origin, size)) {
            problems.add("origin must be [x, y, z] whole numbers inside size");
            origin = new int[3];
        }

        Map<Character, String> palette = new HashMap<>();
        if (!(yaml.get("palette") instanceof Map<?, ?> rawPalette) || rawPalette.isEmpty()) {
            problems.add("palette must map characters to block data");
        } else {
            for (Map.Entry<?, ?> e : rawPalette.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key.length() != 1) {
                    problems.add("palette key '" + key + "' must be exactly one character");
                    continue;
                }
                if (isKeep(key.charAt(0))) {
                    problems.add("palette key '" + key + "' is reserved for 'keep the world's block'");
                    continue;
                }
                if (!(e.getValue() instanceof String data) || data.isBlank()) {
                    problems.add("palette '" + key + "' needs block data like minecraft:oak_planks");
                    continue;
                }
                palette.put(key.charAt(0), data.trim());
            }
        }

        char[][][] grid = new char[size[1]][size[2]][size[0]];
        if (!(yaml.get("layers") instanceof List<?> layers)) {
            problems.add("layers must be a list of layers (bottom to top)");
        } else if (layers.size() != size[1]) {
            problems.add("layers has " + layers.size() + " layers but size y is " + size[1]);
        } else {
            for (int y = 0; y < size[1]; y++) {
                if (!(layers.get(y) instanceof List<?> rows) || rows.size() != size[2]) {
                    problems.add("layer " + y + " must be a list of " + size[2] + " rows");
                    continue;
                }
                for (int z = 0; z < size[2]; z++) {
                    if (!(rows.get(z) instanceof String row) || row.length() != size[0]) {
                        problems.add("layer " + y + " row " + z + " must be a string of " + size[0] + " characters");
                        continue;
                    }
                    for (int x = 0; x < size[0]; x++) {
                        char c = row.charAt(x);
                        if (!isKeep(c) && !palette.containsKey(c)) {
                            problems.add("layer " + y + " row " + z + " uses '" + c + "', which is not in the palette");
                        }
                        grid[y][z][x] = c;
                    }
                }
            }
        }

        Map<String, int[]> points = new LinkedHashMap<>();
        if (yaml.get("points") instanceof Map<?, ?> rawPoints) {
            for (Map.Entry<?, ?> e : rawPoints.entrySet()) {
                int[] p = ints(e.getValue(), 3);
                if (p == null) {
                    problems.add("points." + e.getKey() + " must be [x, y, z] whole numbers");
                } else if (!inside(p, size)) {
                    problems.add("points." + e.getKey() + " " + java.util.Arrays.toString(p) + " is outside size "
                            + java.util.Arrays.toString(size));
                } else {
                    points.put(String.valueOf(e.getKey()), p);
                }
            }
        } else if (yaml.get("points") != null) {
            problems.add("points must be a section");
        }

        List<HouseDesign.SignText> signs = new ArrayList<>();
        if (yaml.get("signs") instanceof List<?> rawSigns) {
            for (int i = 0; i < rawSigns.size(); i++) {
                String where = "signs[" + i + "]";
                if (!(rawSigns.get(i) instanceof Map<?, ?> sign)) {
                    problems.add(where + " must be a section with 'at' and 'lines'");
                    continue;
                }
                int[] at = ints(sign.get("at"), 3);
                if (at == null || !inside(at, size)) {
                    problems.add(where + ".at must be [x, y, z] inside size " + java.util.Arrays.toString(size));
                    continue;
                }
                if (!(sign.get("lines") instanceof List<?> rawLines) || rawLines.isEmpty() || rawLines.size() > 4) {
                    problems.add(where + ".lines must be a list of 1 to 4 strings");
                    continue;
                }
                List<String> lines = new ArrayList<>();
                rawLines.forEach(l -> lines.add(l == null ? "" : String.valueOf(l)));
                char c = grid[at[1]][at[2]][at[0]];
                if (isKeep(c) || c == 0) {
                    problems.add(where + ".at " + java.util.Arrays.toString(at) + " is not a palette block");
                    continue;
                }
                signs.add(new HouseDesign.SignText(at, List.copyOf(lines)));
            }
        } else if (yaml.get("signs") != null) {
            problems.add("signs must be a list");
        }

        return problems.isEmpty()
                ? new Result(new HouseDesign(name, size[0], size[1], size[2], origin, Map.copyOf(palette), grid,
                        points, List.copyOf(signs)), List.of())
                : new Result(null, problems);
    }

    private static boolean inside(int[] p, int[] size) {
        return p[0] >= 0 && p[1] >= 0 && p[2] >= 0 && p[0] < size[0] && p[1] < size[1] && p[2] < size[2];
    }

    private static int @Nullable [] ints(Object raw, int n) {
        if (!(raw instanceof List<?> list) || list.size() != n) {
            return null;
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            if (!(list.get(i) instanceof Number num) || num.doubleValue() != Math.rint(num.doubleValue())) {
                return null;
            }
            out[i] = num.intValue();
        }
        return out;
    }
}
