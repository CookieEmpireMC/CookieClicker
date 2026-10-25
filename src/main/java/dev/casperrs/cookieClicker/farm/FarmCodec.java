package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.plot.Plot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code plots.yml farms:} &lt;-&gt; {@link Farm}s, written by {@code /bakery farm setup}. Layout:
 * <pre>
 * farms:
 *   farm1:
 *     world: minecraft:overworld
 *     region: countryside           # the region with bakery-farm farm1
 *     origin: [100, 64, 200]        # the region's min corner
 *     box: { min: [100, 60, 200], max: [249, 103, 319] }   # everything the paste wrote, ground included
 *     places:
 *       mill:
 *         viewpoint: { x: 120.5, y: 65.0, z: 230.5, yaw: 180.0, pitch: 10.0 }
 *         yard: { from: [115, 225], to: [125, 231], y: 65 }
 *         hologram: { x: .., y: .., z: .. }
 *         panel: { x: .., y: .., z: .., yaw: 180.0 }
 *         ripe: { x: .., y: .., z: .. }
 *         smoke: [ { x: .., y: .., z: .. } ]                 # optional
 *         wheel: { x: .., y: .., z: .., axis: x }            # optional
 *         recipe-book: { x: .., y: .., z: .. }               # optional
 *         notice-board: { x: .., y: .., z: .., yaw: 90.0 }   # optional
 * </pre>
 * A farm with a bad entry is reported and skipped. Pure Java.
 */
public final class FarmCodec {

    public static final String SECTION = "farms";

    private FarmCodec() {
    }

    public static List<Farm> fromMap(@Nullable Map<String, ?> farms, Consumer<String> problems) {
        List<Farm> out = new ArrayList<>();
        if (farms == null) {
            return out;
        }
        for (Map.Entry<String, ?> e : farms.entrySet()) {
            String id = e.getKey().toLowerCase(Locale.ROOT);
            try {
                out.add(farm(id, e.getValue()));
            } catch (IllegalArgumentException ex) {
                problems.accept("plots.yml: farms." + e.getKey() + ": " + ex.getMessage() + "; farm skipped");
            }
        }
        return out;
    }

    public static Map<String, Object> toMap(Collection<Farm> farms) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Farm f : farms) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", f.worldKey());
            m.put("region", f.regionId());
            m.put("origin", List.of(f.origin().x(), f.origin().y(), f.origin().z()));
            Farm.Box b = f.box();
            m.put("box", map("min", List.of(b.minX(), b.minY(), b.minZ()),
                    "max", List.of(b.maxX(), b.maxY(), b.maxZ())));
            Map<String, Object> places = new LinkedHashMap<>();
            f.places().forEach((name, p) -> places.put(name, place(p)));
            m.put("places", places);
            out.put(f.id(), m);
        }
        return out;
    }

    private static Map<String, Object> place(Farm.FarmPlace p) {
        Map<String, Object> m = new LinkedHashMap<>();
        Plot.Viewpoint v = p.viewpoint();
        m.put("viewpoint", xyz(v.x(), v.y(), v.z(), "yaw", v.yaw(), "pitch", v.pitch()));
        Farm.Yard y = p.yard();
        m.put("yard", map("from", List.of(y.minX(), y.minZ()), "to", List.of(y.maxX(), y.maxZ()), "y", y.y()));
        m.put("hologram", point(p.hologram()));
        m.put("panel", pedestal(p.panel()));
        m.put("ripe", point(p.ripe()));
        if (!p.smoke().isEmpty()) {
            m.put("smoke", p.smoke().stream().map(FarmCodec::point).toList());
        }
        if (p.wheel() != null) {
            Plot.Point w = p.wheel().at();
            m.put("wheel", xyz(w.x(), w.y(), w.z(), "axis", p.wheel().axis().name().toLowerCase(Locale.ROOT)));
        }
        if (p.recipeBook() != null) {
            m.put("recipe-book", point(p.recipeBook()));
        }
        if (p.noticeBoard() != null) {
            m.put("notice-board", pedestal(p.noticeBoard()));
        }
        return m;
    }

    private static Farm farm(String id, @Nullable Object raw) {
        if (!Plot.validId(id)) {
            throw new IllegalArgumentException("farm ids may only use a-z, 0-9, _ and - (max 64)");
        }
        Map<?, ?> m = section(raw, "the farm");
        String world = string(m, "world");
        String region = string(m, "region");
        int[] origin = ints(m.get("origin"), 3, "origin");
        Map<?, ?> box = section(m.get("box"), "box");
        int[] min = ints(box.get("min"), 3, "box.min");
        int[] max = ints(box.get("max"), 3, "box.max");
        Map<?, ?> rawPlaces = section(m.get("places"), "places");
        Map<String, Farm.FarmPlace> places = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : rawPlaces.entrySet()) {
            String name = String.valueOf(e.getKey());
            try {
                places.put(name, place(section(e.getValue(), "the place")));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("places." + name + ": " + ex.getMessage(), ex);
            }
        }
        return new Farm(id, world, region, new Plot.Cell(origin[0], origin[1], origin[2]),
                new Farm.Box(min[0], min[1], min[2], max[0], max[1], max[2]), places);
    }

    private static Farm.FarmPlace place(Map<?, ?> m) {
        double[] v = numbers(section(m.get("viewpoint"), "viewpoint"), "viewpoint", "x", "y", "z", "yaw", "pitch");
        Map<?, ?> yard = section(m.get("yard"), "yard");
        int[] from = ints(yard.get("from"), 2, "yard.from");
        int[] to = ints(yard.get("to"), 2, "yard.to");
        if (!(yard.get("y") instanceof Number y)) {
            throw new IllegalArgumentException("yard.y missing");
        }
        List<Plot.Point> smoke = new ArrayList<>();
        if (m.get("smoke") instanceof List<?> list) {
            for (Object o : list) {
                smoke.add(point(o, "smoke"));
            }
        }
        Farm.Wheel wheel = null;
        if (m.get("wheel") != null) {
            Map<?, ?> w = section(m.get("wheel"), "wheel");
            double[] at = numbers(w, "wheel", "x", "y", "z");
            PlacePoints.Axis axis = "z".equals(w.get("axis")) ? PlacePoints.Axis.Z : PlacePoints.Axis.X;
            wheel = new Farm.Wheel(new Plot.Point(at[0], at[1], at[2]), axis);
        }
        return new Farm.FarmPlace(new Plot.Viewpoint(v[0], v[1], v[2], (float) v[3], (float) v[4]),
                new Farm.Yard(from[0], from[1], to[0], to[1], y.intValue()), point(m.get("hologram"), "hologram"),
                pedestal(m.get("panel"), "panel"), point(m.get("ripe"), "ripe"), smoke, wheel,
                m.get("recipe-book") == null ? null : point(m.get("recipe-book"), "recipe-book"),
                m.get("notice-board") == null ? null : pedestal(m.get("notice-board"), "notice-board"));
    }

    private static Map<String, Object> point(Plot.Point p) {
        return xyz(p.x(), p.y(), p.z());
    }

    private static Map<String, Object> pedestal(Plot.Pedestal p) {
        return xyz(p.x(), p.y(), p.z(), "yaw", p.yaw());
    }

    private static Plot.Point point(@Nullable Object raw, String key) {
        double[] v = numbers(section(raw, key), key, "x", "y", "z");
        return new Plot.Point(v[0], v[1], v[2]);
    }

    private static Plot.Pedestal pedestal(@Nullable Object raw, String key) {
        double[] v = numbers(section(raw, key), key, "x", "y", "z", "yaw");
        return new Plot.Pedestal(v[0], v[1], v[2], (float) v[3]);
    }

    private static Map<String, Object> xyz(double x, double y, double z, Object... extra) {
        Map<String, Object> m = map("x", x, "y", y, "z", z);
        for (int i = 0; i + 1 < extra.length; i += 2) {
            m.put((String) extra[i], extra[i + 1] instanceof Float f ? (double) f : extra[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** x, y, z required; any further key (yaw, pitch) defaults to 0. */
    private static double[] numbers(Map<?, ?> m, String where, String... keys) {
        double[] out = new double[keys.length];
        for (int i = 0; i < keys.length; i++) {
            Object v = m.get(keys[i]);
            if (v instanceof Number n) {
                out[i] = n.doubleValue();
            } else if (i < 3 || v != null) {
                throw new IllegalArgumentException(where + "." + keys[i] + " must be a number");
            }
        }
        return out;
    }

    private static Map<?, ?> section(@Nullable Object raw, String what) {
        if (!(raw instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException(what + " must be a section");
        }
        return m;
    }

    private static String string(Map<?, ?> m, String key) {
        if (!(m.get(key) instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException(key + " missing");
        }
        return s;
    }

    private static int[] ints(@Nullable Object raw, int n, String key) {
        if (!(raw instanceof List<?> l) || l.size() != n) {
            throw new IllegalArgumentException(key + " must be a list of " + n + " whole numbers");
        }
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            if (!(l.get(i) instanceof Number num)) {
                throw new IllegalArgumentException(key + " must be a list of " + n + " whole numbers");
            }
            out[i] = num.intValue();
        }
        return out;
    }
}
