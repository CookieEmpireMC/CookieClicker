package dev.casperrs.cookieClicker.station;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Migration only (the one-time {@code stations.yml} to {@code plots.yml} move in {@code PlotService}; delete with it):
 * {@code stations.yml} &lt;-&gt; {@link Station}s. Layout:
 * <pre>
 * stations:
 *   station-1: { world: minecraft:overworld, x: 10.5, y: 64.0, z: -3.5, yaw: 90.0 }
 * </pre>
 * Bad entries are reported and skipped. Pure Java.
 */
public final class StationCodec {

    private static final Pattern WORLD_KEY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private StationCodec() {
    }

    public static List<Station> fromMap(Map<String, ?> root, Consumer<String> problems) {
        List<Station> out = new ArrayList<>();
        if (!(root.get("stations") instanceof Map<?, ?> stations)) {
            return out;
        }
        for (Map.Entry<?, ?> e : stations.entrySet()) {
            String id = String.valueOf(e.getKey());
            String where = "stations.yml: " + id;
            if (!(e.getValue() instanceof Map<?, ?> spec)) {
                problems.accept(where + " must be a section");
                continue;
            }
            Object world = spec.get("world");
            if (!(world instanceof String w) || !WORLD_KEY.matcher(w).matches()) {
                problems.accept(where + " needs 'world' as a world key like minecraft:overworld, got " + world);
                continue;
            }
            Double x = number(spec.get("x"));
            Double y = number(spec.get("y"));
            Double z = number(spec.get("z"));
            Double yaw = spec.containsKey("yaw") ? number(spec.get("yaw")) : Double.valueOf(0);
            if (x == null || y == null || z == null || yaw == null) {
                problems.accept(where + " needs numeric x, y, z (and yaw)");
                continue;
            }
            out.add(new Station(id, w, x, y, z, yaw.floatValue()));
        }
        return out;
    }

    public static Map<String, Object> toMap(Collection<Station> stations) {
        Map<String, Object> all = new LinkedHashMap<>();
        for (Station s : stations) {
            Map<String, Object> spec = new LinkedHashMap<>();
            spec.put("world", s.worldKey());
            spec.put("x", s.x());
            spec.put("y", s.y());
            spec.put("z", s.z());
            spec.put("yaw", (double) s.yaw());
            all.put(s.id(), spec);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("stations", all);
        return root;
    }

    private static Double number(Object raw) {
        return raw instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : null;
    }
}
