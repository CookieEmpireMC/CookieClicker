package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The fixed plot layout of a house design ({@code layout:} in {@code houses/<name>.yml}), used by
 * {@code /bakery plot setup}. Pure data + parser. Every cell is relative to the layout's minimum corner (x, z) and the
 * ground (y 0 = the first air layer above the ground); front = north; never rotated.
 * <pre>
 * layout:
 *   plot-size: [23, 27]              # x, z: the fenced square (fence on its outer ring)
 *   house-at: [5, 0, 3]              # where the design's origin cell goes
 *   pedestal: { at: [11, 0, 18], yaw: 180 }     # the cookie spot; yaw = the way it faces (180 = north, to visitors)
 *   shop: { at: [14, 0, 18], yaw: 180 }         # the shop panel spot, same yaw meaning
 *   clicks: { at: [8, 0, 18], yaw: 180 }        # optional: the click upgrades panel spot, same yaw meaning
 *   viewpoint: { at: [11, 0, 2], yaw: 0, pitch: -14 }   # player view of the build cinematic (yaw 0 = south)
 *   entrance: [11, 0]                # x, z of the gate centre (on the outer ring)
 *   belt: { from: [11, 0, 12], to: [11, 0, 19] }          # optional: conveyor path; to = the last belt cell
 *   camera: { at: [15, 9, -8], yaw: 0, pitch: 30 }        # optional: raised cinematic camera, may be outside the plot
 * </pre>
 * The belt must be straight (same y, and the same x or z), at most {@link #MAX_BELT} cells.
 * Cell positions become block centres in x/z ({@code + 0.5}); y stays the cell's bottom.
 */
public record HouseLayout(int sizeX, int sizeZ, int[] houseAt, Spot pedestal, Spot shop, View viewpoint,
                          int[] entrance, int @Nullable [] beltFrom, int @Nullable [] beltTo,
                          @Nullable View camera, @Nullable Spot clicks) {

    public static final int MAX_SIZE = 256;
    public static final int MAX_BELT = 64;

    /** A layout cell with a yaw. */
    public record Spot(double[] at, float yaw) {
    }

    /** A layout cell with yaw and pitch. */
    public record View(double[] at, float yaw, float pitch) {
    }

    /** Parses {@code layout:}; adds a problem for every bad key and returns null then. */
    public static @Nullable HouseLayout parse(Object raw, List<String> problems) {
        if (!(raw instanceof Map<?, ?> m)) {
            problems.add("layout must be a section");
            return null;
        }
        int before = problems.size();
        int[] size = ints(m.get("plot-size"), 2);
        if (size == null || size[0] < 5 || size[1] < 5 || size[0] > MAX_SIZE || size[1] > MAX_SIZE) {
            problems.add("layout.plot-size must be [x, z] whole numbers from 5 to " + MAX_SIZE);
            size = new int[] {5, 5};
        }
        int[] houseAt = ints(m.get("house-at"), 3);
        if (houseAt == null) {
            problems.add("layout.house-at must be [x, y, z] whole numbers");
        }
        Spot pedestal = spot(m.get("pedestal"), "pedestal", size, problems);
        Spot shop = spot(m.get("shop"), "shop", size, problems);
        Spot clicks = m.get("clicks") == null ? null : spot(m.get("clicks"), "clicks", size, problems);
        View viewpoint = null;
        if (m.get("viewpoint") instanceof Map<?, ?> v) {
            double[] at = cell(v.get("at"), "viewpoint", size, problems);
            Float yaw = number(v.get("yaw"));
            Float pitch = v.get("pitch") == null ? Float.valueOf(0f) : number(v.get("pitch"));
            if (yaw == null || pitch == null) {
                problems.add("layout.viewpoint needs numeric yaw (and pitch)");
            } else if (at != null) {
                viewpoint = new View(at, yaw, Math.clamp(pitch, -90f, 90f));
            }
        } else {
            problems.add("layout.viewpoint must be { at: [x, y, z], yaw, pitch }");
        }
        int[] entrance = ints(m.get("entrance"), 2);
        if (entrance == null || entrance[0] < 0 || entrance[1] < 0 || entrance[0] >= size[0] || entrance[1] >= size[1]
                || (entrance[0] != 0 && entrance[1] != 0 && entrance[0] != size[0] - 1 && entrance[1] != size[1] - 1)) {
            problems.add("layout.entrance must be [x, z] on the outer ring of plot-size");
        }
        int[] beltFrom = null;
        int[] beltTo = null;
        if (m.get("belt") != null) {
            if (m.get("belt") instanceof Map<?, ?> b) {
                beltFrom = ints(b.get("from"), 3);
                beltTo = ints(b.get("to"), 3);
            }
            if (beltFrom == null || beltTo == null) {
                problems.add("layout.belt must be { from: [x, y, z], to: [x, y, z] }");
            } else if (beltFrom[1] != beltTo[1] || (beltFrom[0] != beltTo[0] && beltFrom[2] != beltTo[2])
                    || Math.abs(beltFrom[0] - beltTo[0]) + Math.abs(beltFrom[2] - beltTo[2]) >= MAX_BELT) {
                problems.add("layout.belt must be a straight line (same y, same x or z) of at most " + MAX_BELT
                        + " cells");
            }
        }
        View camera = null;
        if (m.get("camera") != null) {
            camera = camera(m.get("camera"), problems);
        }
        if (problems.size() > before) {
            return null;
        }
        return new HouseLayout(size[0], size[1], houseAt, pedestal, shop, viewpoint, entrance, beltFrom, beltTo,
                camera, clicks);
    }

    /** {@code camera}: any numbers within {@link #MAX_SIZE} of the layout corner (outside the plot is fine). */
    private static @Nullable View camera(Object raw, List<String> problems) {
        if (raw instanceof Map<?, ?> c && c.get("at") instanceof List<?> l && l.size() == 3
                && l.get(0) instanceof Number x && l.get(1) instanceof Number y && l.get(2) instanceof Number z) {
            double[] at = {x.doubleValue(), y.doubleValue(), z.doubleValue()};
            Float yaw = c.get("yaw") == null ? Float.valueOf(0f) : number(c.get("yaw"));
            Float pitch = c.get("pitch") == null ? Float.valueOf(0f) : number(c.get("pitch"));
            boolean near = true;
            for (double v : at) {
                near &= Double.isFinite(v) && Math.abs(v) <= MAX_SIZE;
            }
            if (yaw != null && pitch != null && near) {
                return new View(at, yaw, Math.clamp(pitch, -90f, 90f));
            }
        }
        problems.add("layout.camera must be { at: [x, y, z], yaw, pitch } with numbers within " + MAX_SIZE
                + " of the layout corner");
        return null;
    }

    private static @Nullable Spot spot(Object raw, String key, int[] size, List<String> problems) {
        if (!(raw instanceof Map<?, ?> s)) {
            problems.add("layout." + key + " must be { at: [x, y, z], yaw }");
            return null;
        }
        double[] at = cell(s.get("at"), key, size, problems);
        Float yaw = s.get("yaw") == null ? Float.valueOf(0f) : number(s.get("yaw"));
        if (yaw == null) {
            problems.add("layout." + key + ".yaw must be a number");
            return null;
        }
        return at == null ? null : new Spot(at, yaw);
    }

    /** A cell inside plot-size (y free), as numbers. */
    private static double @Nullable [] cell(Object raw, String key, int[] size, List<String> problems) {
        if (!(raw instanceof List<?> l) || l.size() != 3 || !(l.get(0) instanceof Number x)
                || !(l.get(1) instanceof Number y) || !(l.get(2) instanceof Number z)) {
            problems.add("layout." + key + ".at must be [x, y, z]");
            return null;
        }
        double[] at = {x.doubleValue(), y.doubleValue(), z.doubleValue()};
        if (at[0] < 0 || at[2] < 0 || at[0] >= size[0] || at[2] >= size[1] || Math.abs(at[1]) > MAX_SIZE) {
            problems.add("layout." + key + ".at " + java.util.Arrays.toString(at) + " is outside plot-size");
            return null;
        }
        return at;
    }

    private static @Nullable Float number(Object raw) {
        return raw instanceof Number n && Double.isFinite(n.doubleValue()) ? n.floatValue() : null;
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
