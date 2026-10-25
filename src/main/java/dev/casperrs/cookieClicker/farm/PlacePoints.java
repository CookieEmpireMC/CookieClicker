package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseDesignParser;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The {@code points:} of a countryside place file, in design-local cells (y counts from the first layer above the
 * ground, like every house design point). Pure. Format (spec: design/countryside.md):
 * <pre>
 * points:
 *   viewpoint: {at: [x, y, z], yaw: 0, pitch: 10}   # required: the feet cell players arrive on
 *   yard: {from: [x, z], to: [x, z]}                # required: the front yard, inclusive
 *   hologram: [x, y, z]                             # required
 *   panel: {at: [x, y, z], yaw: 180}                # required
 *   ripe: [x, y, z]                                 # required
 *   smoke: [[x, y, z]]                              # optional: at most one
 *   wheel: {at: [x, y, z], axis: x}                 # optional (the mill): axle cell, axis x or z
 *   recipe_book: [x, y, z]                          # optional (the market)
 *   notice_board: {at: [x, y, z], yaw: 90}          # optional (the market)
 * </pre>
 */
public record PlacePoints(Spot viewpoint, Yard yard, Cell hologram, Spot panel, Cell ripe, @Nullable Cell smoke,
                          @Nullable Wheel wheel, @Nullable Cell recipeBook, @Nullable Spot noticeBoard) {

    public static final String VIEWPOINT = "viewpoint";
    public static final String YARD = "yard";
    public static final String HOLOGRAM = "hologram";
    public static final String PANEL = "panel";
    public static final String RIPE = "ripe";
    public static final String WHEEL = "wheel";
    public static final String RECIPE_BOOK = "recipe_book";
    public static final String NOTICE_BOARD = "notice_board";

    /** A design cell. */
    public record Cell(int x, int y, int z) {
    }

    /** A cell with a facing (yaw/pitch in degrees, Minecraft convention). */
    public record Spot(Cell at, float yaw, float pitch) {
    }

    /** An inclusive x/z rectangle; from is the smaller corner. */
    public record Yard(int fromX, int fromZ, int toX, int toZ) {
    }

    public enum Axis { X, Z }

    public record Wheel(Cell at, Axis axis) {
    }

    /** The points, or null with at least one problem (prefixed with {@code points.}). */
    public static @Nullable PlacePoints parse(HouseDesign design, @Nullable Object raw, List<String> problems) {
        if (!(raw instanceof Map<?, ?> points)) {
            problems.add("points must be a section with viewpoint, yard, hologram, panel and ripe");
            return null;
        }
        int before = problems.size();
        Reader r = new Reader(design, points, problems);
        Spot viewpoint = r.spot(VIEWPOINT, true, true);
        Yard yard = r.yard();
        Cell hologram = r.cell(HOLOGRAM, true);
        Spot panel = r.spot(PANEL, true, false);
        Cell ripe = r.cell(RIPE, true);
        Wheel wheel = r.wheel();
        Cell recipeBook = r.cell(RECIPE_BOOK, false);
        Spot noticeBoard = r.spot(NOTICE_BOARD, false, false);
        if (design.smoke().size() > 1) {
            problems.add("points.smoke may hold at most one cell (the only chimney)");
        }
        Cell smoke = design.smoke().isEmpty() ? null : cell(design.smoke().getFirst());
        if (problems.size() > before || viewpoint == null || yard == null || hologram == null || panel == null
                || ripe == null) {
            return null;
        }
        return new PlacePoints(viewpoint, yard, hologram, panel, ripe, smoke, wheel, recipeBook, noticeBoard);
    }

    private static Cell cell(int[] c) {
        return new Cell(c[0], c[1], c[2]);
    }

    /** Reads single points out of the raw section and reports each problem once. */
    private record Reader(HouseDesign design, Map<?, ?> points, List<String> problems) {

        @Nullable Cell cell(String key, boolean required) {
            Object raw = points.get(key);
            if (raw == null) {
                missing(key, required);
                return null;
            }
            return inside(key, HouseDesignParser.ints(raw, 3));
        }

        @Nullable Spot spot(String key, boolean required, boolean withPitch) {
            Object raw = points.get(key);
            if (raw == null) {
                missing(key, required);
                return null;
            }
            if (!(raw instanceof Map<?, ?> m)) {
                problems.add("points." + key + " must be {at: [x, y, z], yaw: <degrees>"
                        + (withPitch ? ", pitch: <degrees>}" : "}"));
                return null;
            }
            Cell at = inside(key + ".at", HouseDesignParser.ints(m.get("at"), 3));
            Float yaw = angle(key + ".yaw", m.get("yaw"));
            Float pitch = withPitch ? angle(key + ".pitch", m.get("pitch")) : Float.valueOf(0f);
            return at == null || yaw == null || pitch == null ? null : new Spot(at, yaw, pitch);
        }

        @Nullable Yard yard() {
            Object raw = points.get(YARD);
            if (raw == null) {
                missing(YARD, true);
                return null;
            }
            int[] from = raw instanceof Map<?, ?> m ? HouseDesignParser.ints(m.get("from"), 2) : null;
            int[] to = raw instanceof Map<?, ?> m ? HouseDesignParser.ints(m.get("to"), 2) : null;
            if (from == null || to == null) {
                problems.add("points.yard must be {from: [x, z], to: [x, z]}");
                return null;
            }
            for (int[] c : new int[][] {from, to}) {
                if (c[0] < 0 || c[1] < 0 || c[0] >= design.sizeX() || c[1] >= design.sizeZ()) {
                    problems.add("points.yard corner " + Arrays.toString(c) + " is outside size x/z "
                            + design.sizeX() + " x " + design.sizeZ());
                    return null;
                }
            }
            return new Yard(Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.max(from[0], to[0]),
                    Math.max(from[1], to[1]));
        }

        @Nullable Wheel wheel() {
            Object raw = points.get(WHEEL);
            if (raw == null) {
                return null;
            }
            if (!(raw instanceof Map<?, ?> m)) {
                problems.add("points.wheel must be {at: [x, y, z], axis: x|z}");
                return null;
            }
            Cell at = inside(WHEEL + ".at", HouseDesignParser.ints(m.get("at"), 3));
            Object axis = m.get("axis");
            Axis a = "x".equals(axis) ? Axis.X : "z".equals(axis) ? Axis.Z : null;
            if (a == null) {
                problems.add("points.wheel.axis must be x or z");
            }
            return at == null || a == null ? null : new Wheel(at, a);
        }

        private @Nullable Cell inside(String key, int @Nullable [] p) {
            if (p == null) {
                problems.add("points." + key + " must be [x, y, z] whole numbers");
                return null;
            }
            if (!HouseDesignParser.inside(p, design.size(), design.groundDepth())) {
                problems.add("points." + key + " " + Arrays.toString(p) + " is outside size "
                        + Arrays.toString(design.size()));
                return null;
            }
            return PlacePoints.cell(p);
        }

        private @Nullable Float angle(String key, @Nullable Object raw) {
            if (raw == null) {
                return 0f;
            }
            if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                problems.add("points." + key + " must be a number of degrees");
                return null;
            }
            return n.floatValue();
        }

        private void missing(String key, boolean required) {
            if (required) {
                problems.add("points." + key + " is missing (required for every place)");
            }
        }
    }
}
