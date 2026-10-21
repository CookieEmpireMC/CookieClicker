package dev.casperrs.cookieClicker.plot;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * {@code plots.yml} &lt;-&gt; {@link Plot}s. Layout:
 * <pre>
 * plots:
 *   main:
 *     world: minecraft:overworld
 *     plot-region: bakery_plot      # optional fallback; the bakery-plot flag is the source of truth
 *     house-region: bakery_house    # optional fallback; the bakery-house flag is the source of truth
 *     viewpoint: { x: 10.5, y: 64.0, z: -3.5, yaw: 90.0, pitch: 10.0 }
 *     pedestal: { x: 12.5, y: 64.0, z: -3.5, yaw: 90.0 }
 *     chimney: { x: 20.5, y: 72.0, z: 1.5 }   # optional: one smoke point (written when there is exactly one)
 *     smoke: [ { x: 20.5, y: 72.0, z: 1.5 }, ... ]   # optional: several smoke points (factory smokestacks)
 *     steam: [ { x: 18.5, y: 70.0, z: 2.5 }, ... ]   # optional: factory_steam vents
 *     entrance: { x: 3.5, y: 64.0, z: -9.5 }         # optional: fence gate centre (/bakery plot setup)
 *     shop: { x: 14.5, y: 64.0, z: -3.5, yaw: 90.0 } # optional: shop panel spot (/bakery plot setup)
 *     clicks: { x: 10.5, y: 64.0, z: -3.5, yaw: 90.0 } # optional: click upgrades panel spot (/bakery plot setup)
 *     house-box: { min: [10, 64, -8], max: [22, 78, 2] }   # optional: the house placed by /bakery plot setup
 *     belt: { height: 0.5, cells: [[18, 64, 11], [18, 64, 10], ...] }   # optional: conveyor, hatch -> pedestal
 *     camera: { x: 15.5, y: 73.0, z: -8.5, yaw: 0.0, pitch: 30.0 }      # optional: raised cinematic camera
 * </pre>
 * Bad entries are reported and skipped; a bad optional point is reported and left out. Pure Java.
 */
public final class PlotCodec {

    private static final Pattern WORLD_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private PlotCodec() {
    }

    public static List<Plot> fromMap(Map<String, ?> root, Consumer<String> problems) {
        List<Plot> out = new ArrayList<>();
        if (!(root.get("plots") instanceof Map<?, ?> plots)) {
            return out;
        }
        for (Map.Entry<?, ?> e : plots.entrySet()) {
            String id = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            String where = "plots.yml: " + e.getKey();
            if (!Plot.validId(id)) {
                problems.accept(where + ": plot ids may only use a-z, 0-9, _ and - (max 64)");
                continue;
            }
            if (!(e.getValue() instanceof Map<?, ?> spec)) {
                problems.accept(where + " must be a section");
                continue;
            }
            Object world = spec.get("world");
            if (!(world instanceof String w) || !WORLD_KEY.matcher(w).matches()) {
                problems.accept(where + " needs 'world' as a world key like minecraft:overworld, got " + world);
                continue;
            }
            Plot.Viewpoint viewpoint = null;
            if (spec.get("viewpoint") != null) {
                double[] v = numbers(spec.get("viewpoint"), "x", "y", "z", "yaw", "pitch");
                if (v == null) {
                    problems.accept(where + ".viewpoint needs numeric x, y, z (yaw, pitch optional); ignored");
                } else {
                    viewpoint = new Plot.Viewpoint(v[0], v[1], v[2], (float) v[3], (float) v[4]);
                }
            }
            Plot.Pedestal pedestal = null;
            if (spec.get("pedestal") != null) {
                double[] v = numbers(spec.get("pedestal"), "x", "y", "z", "yaw");
                if (v == null) {
                    problems.accept(where + ".pedestal needs numeric x, y, z (yaw optional); ignored");
                } else {
                    pedestal = new Plot.Pedestal(v[0], v[1], v[2], (float) v[3]);
                }
            }
            List<Plot.Point> smoke = new ArrayList<>();
            if (spec.get("chimney") != null) {
                Plot.Point c = point(spec.get("chimney"));
                if (c == null) {
                    problems.accept(where + ".chimney needs numeric x, y, z; ignored");
                } else {
                    smoke.add(c);
                }
            }
            smoke.addAll(points(spec.get("smoke"), where + ".smoke", problems));
            List<Plot.Point> steam = points(spec.get("steam"), where + ".steam", problems);
            Plot.Point entrance = null;
            if (spec.get("entrance") != null) {
                entrance = point(spec.get("entrance"));
                if (entrance == null) {
                    problems.accept(where + ".entrance needs numeric x, y, z; ignored");
                }
            }
            Plot.Pedestal shop = null;
            if (spec.get("shop") != null) {
                double[] v = numbers(spec.get("shop"), "x", "y", "z", "yaw");
                if (v == null) {
                    problems.accept(where + ".shop needs numeric x, y, z (yaw optional); ignored");
                } else {
                    shop = new Plot.Pedestal(v[0], v[1], v[2], (float) v[3]);
                }
            }
            Plot.Pedestal clicks = null;
            if (spec.get("clicks") != null) {
                double[] v = numbers(spec.get("clicks"), "x", "y", "z", "yaw");
                if (v == null) {
                    problems.accept(where + ".clicks needs numeric x, y, z (yaw optional); ignored");
                } else {
                    clicks = new Plot.Pedestal(v[0], v[1], v[2], (float) v[3]);
                }
            }
            Plot.Box house = null;
            if (spec.get("house-box") != null) {
                house = box(spec.get("house-box"));
                if (house == null) {
                    problems.accept(where + ".house-box needs min: [x, y, z] and max: [x, y, z]; ignored");
                }
            }
            Plot.Belt belt = null;
            if (spec.get("belt") != null) {
                belt = belt(spec.get("belt"));
                if (belt == null) {
                    problems.accept(where + ".belt needs height and cells: [[x, y, z], ...] (at least one); ignored");
                }
            }
            Plot.Viewpoint camera = null;
            if (spec.get("camera") != null) {
                double[] v = numbers(spec.get("camera"), "x", "y", "z", "yaw", "pitch");
                if (v == null) {
                    problems.accept(where + ".camera needs numeric x, y, z (yaw, pitch optional); ignored");
                } else {
                    camera = new Plot.Viewpoint(v[0], v[1], v[2], (float) v[3], (float) v[4]);
                }
            }
            out.add(new Plot(id, w, regionId(spec.get("plot-region")), regionId(spec.get("house-region")),
                    viewpoint, pedestal, smoke, steam, entrance, shop, house, belt, camera, clicks));
        }
        return out;
    }

    public static Map<String, Object> toMap(Collection<Plot> plots) {
        Map<String, Object> all = new LinkedHashMap<>();
        for (Plot p : plots) {
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("world", p.worldKey());
            if (p.plotRegion() != null) {
                spec.put("plot-region", p.plotRegion());
            }
            if (p.houseRegion() != null) {
                spec.put("house-region", p.houseRegion());
            }
            if (p.viewpoint() != null) {
                Plot.Viewpoint v = p.viewpoint();
                spec.put("viewpoint", point(v.x(), v.y(), v.z(), "yaw", v.yaw(), "pitch", v.pitch()));
            }
            if (p.pedestal() != null) {
                Plot.Pedestal v = p.pedestal();
                spec.put("pedestal", point(v.x(), v.y(), v.z(), "yaw", v.yaw(), null, 0));
            }
            if (p.smoke().size() == 1) {
                Plot.Point v = p.smoke().getFirst();
                spec.put("chimney", point(v.x(), v.y(), v.z(), null, 0, null, 0));
            } else if (!p.smoke().isEmpty()) {
                spec.put("smoke", pointList(p.smoke()));
            }
            if (!p.steam().isEmpty()) {
                spec.put("steam", pointList(p.steam()));
            }
            if (p.entrance() != null) {
                Plot.Point v = p.entrance();
                spec.put("entrance", point(v.x(), v.y(), v.z(), null, 0, null, 0));
            }
            if (p.shop() != null) {
                Plot.Pedestal v = p.shop();
                spec.put("shop", point(v.x(), v.y(), v.z(), "yaw", v.yaw(), null, 0));
            }
            if (p.clicks() != null) {
                Plot.Pedestal v = p.clicks();
                spec.put("clicks", point(v.x(), v.y(), v.z(), "yaw", v.yaw(), null, 0));
            }
            if (p.house() != null) {
                Plot.Box b = p.house();
                Map<String, Object> box = new LinkedHashMap<>();
                box.put("min", List.of(b.minX(), b.minY(), b.minZ()));
                box.put("max", List.of(b.maxX(), b.maxY(), b.maxZ()));
                spec.put("house-box", box);
            }
            if (p.belt() != null) {
                Map<String, Object> belt = new LinkedHashMap<>();
                belt.put("height", p.belt().height());
                List<Object> cells = new ArrayList<>();
                for (Plot.Cell c : p.belt().cells()) {
                    cells.add(List.of(c.x(), c.y(), c.z()));
                }
                belt.put("cells", cells);
                spec.put("belt", belt);
            }
            if (p.camera() != null) {
                Plot.Viewpoint v = p.camera();
                spec.put("camera", point(v.x(), v.y(), v.z(), "yaw", v.yaw(), "pitch", v.pitch()));
            }
            all.put(p.id(), spec);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("plots", all);
        return root;
    }

    private static Map<String, Object> point(double x, double y, double z, @Nullable String k1, float v1,
                                             @Nullable String k2, float v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        if (k1 != null) {
            m.put(k1, (double) v1);
        }
        if (k2 != null) {
            m.put(k2, (double) v2);
        }
        return m;
    }

    private static Plot.@Nullable Box box(Object raw) {
        if (!(raw instanceof Map<?, ?> m) || !(m.get("min") instanceof List<?> min) || !(m.get("max") instanceof List<?> max)
                || min.size() != 3 || max.size() != 3) {
            return null;
        }
        int[] v = new int[6];
        for (int i = 0; i < 6; i++) {
            if (!((i < 3 ? min.get(i) : max.get(i - 3)) instanceof Number n)) {
                return null;
            }
            v[i] = n.intValue();
        }
        return new Plot.Box(Math.min(v[0], v[3]), Math.min(v[1], v[4]), Math.min(v[2], v[5]),
                Math.max(v[0], v[3]), Math.max(v[1], v[4]), Math.max(v[2], v[5]));
    }

    private static Plot.@Nullable Belt belt(Object raw) {
        if (!(raw instanceof Map<?, ?> m) || !(m.get("cells") instanceof List<?> list) || list.isEmpty()
                || !(m.get("height") instanceof Number h) || !Double.isFinite(h.doubleValue())) {
            return null;
        }
        List<Plot.Cell> cells = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof List<?> c) || c.size() != 3 || !(c.get(0) instanceof Number x)
                    || !(c.get(1) instanceof Number y) || !(c.get(2) instanceof Number z)) {
                return null;
            }
            cells.add(new Plot.Cell(x.intValue(), y.intValue(), z.intValue()));
        }
        return new Plot.Belt(cells, h.doubleValue());
    }

    private static List<Object> pointList(List<Plot.Point> points) {
        List<Object> out = new ArrayList<>(points.size());
        for (Plot.Point v : points) {
            out.add(point(v.x(), v.y(), v.z(), null, 0, null, 0));
        }
        return out;
    }

    private static Plot.@Nullable Point point(Object raw) {
        double[] v = numbers(raw, "x", "y", "z");
        return v == null ? null : new Plot.Point(v[0], v[1], v[2]);
    }

    /** A list of {x, y, z} sections; bad entries are reported and skipped. Null/absent = empty. */
    private static List<Plot.Point> points(@Nullable Object raw, String where, Consumer<String> problems) {
        List<Plot.Point> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        if (!(raw instanceof List<?> list)) {
            problems.accept(where + " must be a list of { x, y, z }; ignored");
            return out;
        }
        for (int i = 0; i < list.size(); i++) {
            Plot.Point p = point(list.get(i));
            if (p == null) {
                problems.accept(where + "[" + i + "] needs numeric x, y, z; ignored");
            } else {
                out.add(p);
            }
        }
        return out;
    }

    /** x, y, z required; any further keys default to 0. Null when not a section or a required value is missing. */
    private static double @Nullable [] numbers(Object raw, String... keys) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        double[] out = new double[keys.length];
        for (int i = 0; i < keys.length; i++) {
            Object v = m.get(keys[i]);
            if (v == null && i >= 3) {
                continue;
            }
            if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue())) {
                return null;
            }
            out[i] = n.doubleValue();
        }
        return out;
    }

    private static @Nullable String regionId(Object raw) {
        return raw instanceof String s && !s.isBlank() ? s.trim().toLowerCase(Locale.ROOT) : null;
    }
}
