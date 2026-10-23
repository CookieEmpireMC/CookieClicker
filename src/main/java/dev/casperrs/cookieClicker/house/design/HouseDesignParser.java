package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a house design file. Pure Java over the YAML as plain maps:
 * <pre>
 * size: [9, 7, 7]            # x (row length), y (number of layers), z (rows per layer)
 * origin: [0, 0, 0]          # optional: the design cell placed on the house region's minimum corner
 * palette:                   # one character -> block data
 *   "P": "minecraft:oak_planks"
 *   "_": "minecraft:air"     # to clear a cell, map a character to air
 * layers:                    # bottom -> top; each layer is a list of rows (north -> south),
 *   - ["PPPPPPPPP", ...]     # each row a string of characters (west -> east)
 * points:
 *   chimney: [7, 7, 1]       # the cell the smoke starts in (smoke plays at its bottom centre); = smoke: [[7, 7, 1]]
 *   smoke: [[3, 9, 1], [8, 7, 2]]   # optional: several smoke start cells (used instead of chimney when given)
 *   steam: [[5, 6, 3]]       # optional: factory_steam vents
 *   belt_out: [4, 1, 8]      # optional: where the conveyor belt leaves the building
 *   door: [4, 1, 6]          # optional; every point must be inside size
 * layout: ...                # optional: the fixed plot layout for /bakery plot setup, see HouseLayout
 * signs:                     # optional: plain text set on the sign's front side after pasting
 *   - at: [5, 2, 0]
 *     lines: ["", "Bakery", "", ""]
 * </pre>
 * {@code .} and space mean "keep the world's block" (no change, no backup entry). Spec: design/house-default.md.
 * Every problem is collected; the design is returned only when there were none.
 */
public final class HouseDesignParser {

    public static final char KEEP = '.';
    public static final String CHIMNEY = "chimney";
    public static final String SMOKE = "smoke";
    public static final String STEAM = "steam";
    public static final String BELT_OUT = "belt_out";
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
        Map<Character, String> palette = palette(yaml.get("palette"), problems);
        char[][][] grid = grid(yaml.get("layers"), size, palette, problems);
        Map<String, int[]> points = new LinkedHashMap<>();
        List<int[]> smoke = new ArrayList<>();
        List<int[]> steam = new ArrayList<>();
        points(yaml.get("points"), size, points, smoke, steam, problems);
        if (smoke.isEmpty() && points.get(CHIMNEY) != null) {
            smoke.add(points.get(CHIMNEY)); // a single chimney = a one-item smoke list
        }
        List<HouseDesign.SignText> signs = signs(yaml.get("signs"), size, grid, problems);
        HouseLayout layout = yaml.get("layout") == null ? null : layout(yaml.get("layout"), size, origin, problems);
        return problems.isEmpty()
                ? new Result(new HouseDesign(name, size[0], size[1], size[2], origin, Map.copyOf(palette), grid,
                        points, List.copyOf(smoke), List.copyOf(steam), List.copyOf(signs), layout), List.of())
                : new Result(null, problems);
    }

    private static Map<Character, String> palette(@Nullable Object raw, List<String> problems) {
        Map<Character, String> palette = new HashMap<>();
        if (!(raw instanceof Map<?, ?> rawPalette) || rawPalette.isEmpty()) {
            problems.add("palette must map characters to block data");
            return palette;
        }
        for (Map.Entry<?, ?> e : rawPalette.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.length() != 1) {
                problems.add("palette key '" + key + "' must be exactly one character");
            } else if (isKeep(key.charAt(0))) {
                problems.add("palette key '" + key + "' is reserved for 'keep the world's block'");
            } else if (!(e.getValue() instanceof String data) || data.isBlank()) {
                problems.add("palette '" + key + "' needs block data like minecraft:oak_planks");
            } else {
                palette.put(key.charAt(0), data.trim());
            }
        }
        return palette;
    }

    private static char[][][] grid(@Nullable Object raw, int[] size, Map<Character, String> palette,
                                   List<String> problems) {
        char[][][] grid = new char[size[1]][size[2]][size[0]];
        if (!(raw instanceof List<?> layers)) {
            problems.add("layers must be a list of layers (bottom to top)");
            return grid;
        }
        if (layers.size() != size[1]) {
            problems.add("layers has " + layers.size() + " layers but size y is " + size[1]);
            return grid;
        }
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
        return grid;
    }

    private static void points(@Nullable Object raw, int[] size, Map<String, int[]> points, List<int[]> smoke,
                               List<int[]> steam, List<String> problems) {
        if (!(raw instanceof Map<?, ?> rawPoints)) {
            if (raw != null) {
                problems.add("points must be a section");
            }
            return;
        }
        for (Map.Entry<?, ?> e : rawPoints.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key.equals(SMOKE) || key.equals(STEAM)) {
                pointList(key, e.getValue(), size, key.equals(SMOKE) ? smoke : steam, problems);
                continue;
            }
            int[] p = ints(e.getValue(), 3);
            if (p == null) {
                problems.add("points." + key + " must be [x, y, z] whole numbers");
            } else if (!inside(p, size)) {
                problems.add("points." + key + " " + Arrays.toString(p) + " is outside size " + Arrays.toString(size));
            } else {
                points.put(key, p);
            }
        }
    }

    private static List<HouseDesign.SignText> signs(@Nullable Object raw, int[] size, char[][][] grid,
                                                    List<String> problems) {
        List<HouseDesign.SignText> signs = new ArrayList<>();
        if (!(raw instanceof List<?> rawSigns)) {
            if (raw != null) {
                problems.add("signs must be a list");
            }
            return signs;
        }
        for (int i = 0; i < rawSigns.size(); i++) {
            String where = "signs[" + i + "]";
            if (!(rawSigns.get(i) instanceof Map<?, ?> sign)) {
                problems.add(where + " must be a section with 'at' and 'lines'");
                continue;
            }
            int[] at = ints(sign.get("at"), 3);
            if (at == null || !inside(at, size)) {
                problems.add(where + ".at must be [x, y, z] inside size " + Arrays.toString(size));
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
                problems.add(where + ".at " + Arrays.toString(at) + " is not a palette block");
                continue;
            }
            signs.add(new HouseDesign.SignText(at, List.copyOf(lines)));
        }
        return signs;
    }

    /** The layout, which must leave the house inside the fence ring. */
    private static @Nullable HouseLayout layout(Object raw, int[] size, int[] origin, List<String> problems) {
        HouseLayout layout = HouseLayout.parse(raw, problems);
        if (layout != null) {
            int[] min = {layout.houseAt()[0] - origin[0], layout.houseAt()[2] - origin[2]};
            if (min[0] < 1 || min[1] < 1 || min[0] + size[0] > layout.sizeX() - 1
                    || min[1] + size[2] > layout.sizeZ() - 1) {
                problems.add("layout.house-at puts the house (" + size[0] + " x " + size[2]
                        + ") outside the area inside the fence (plot-size minus the outer ring)");
            }
        }
        return layout;
    }

    /** {@code points.smoke} / {@code points.steam}: a list of [x, y, z], each inside size. */
    private static void pointList(String key, Object raw, int[] size, List<int[]> out, List<String> problems) {
        if (!(raw instanceof List<?> list)) {
            problems.add("points." + key + " must be a list of [x, y, z]");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            int[] p = ints(list.get(i), 3);
            if (p == null) {
                problems.add("points." + key + "[" + i + "] must be [x, y, z] whole numbers");
            } else if (!inside(p, size)) {
                problems.add("points." + key + "[" + i + "] " + Arrays.toString(p) + " is outside size "
                        + Arrays.toString(size));
            } else {
                out.add(p);
            }
        }
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
