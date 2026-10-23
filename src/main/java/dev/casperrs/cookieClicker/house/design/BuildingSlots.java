package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where the bought bakery buildings stand on the factory plot ({@code houses/building-slots.yml}, spec
 * design/buildings-world.md). Pure data and parser over the YAML as plain
 * maps:
 * <pre>
 * slots:
 *   villager_baker:
 *     - {at: [10, 0, 5], yaw: 180}
 *     - {at: [11, 0, 5], yaw: 180}
 * </pre>
 * {@code at} is a plot cell from the plot's min corner (the frame of {@code layout:} in the house file), y 0 = the
 * first air layer above the ground; {@code yaw} is the way the building faces (180 = north, like the layout spots).
 * Instance <i>i</i> of a building stands on slot <i>i</i>; the slot count is the visual cap (at most
 * {@link #MAX_PER_TYPE}). A bad entry is a problem and is skipped; the rest still counts.
 */
public record BuildingSlots(Map<String, List<Slot>> slots) {

    /** The visual cap per building type. */
    public static final int MAX_PER_TYPE = 10;
    private static final int MIN_Y = -16;
    private static final int MAX_Y = 64;

    public BuildingSlots {
        Map<String, List<Slot>> copy = new LinkedHashMap<>();
        slots.forEach((id, list) -> copy.put(id, List.copyOf(list)));
        slots = java.util.Collections.unmodifiableMap(copy);
    }

    /** One slot: a plot cell and the way the building there faces. */
    public record Slot(int x, int y, int z, float yaw) {
    }

    /** The parsed slots (never null; empty on a broken file) and every problem found. */
    public record Result(BuildingSlots slots, List<String> problems) {
        public Result {
            problems = List.copyOf(problems);
        }
    }

    public static BuildingSlots empty() {
        return new BuildingSlots(Map.of());
    }

    /** The slots of {@code buildingId} (empty when it has none). */
    public List<Slot> of(String buildingId) {
        return slots.getOrDefault(buildingId, List.of());
    }

    /** Every slot cell as {@code [x, z]} (the decor keep-out). */
    public List<int[]> columns() {
        List<int[]> out = new ArrayList<>();
        slots.values().forEach(list -> list.forEach(s -> out.add(new int[] {s.x(), s.z()})));
        return out;
    }

    public static Result parse(Map<String, ?> yaml) {
        List<String> problems = new ArrayList<>();
        Object raw = yaml.get("slots");
        if (!(raw instanceof Map<?, ?> map)) {
            problems.add(raw == null ? "slots missing" : "slots must be a map of building id -> list of slots");
            return new Result(empty(), problems);
        }
        Map<String, List<Slot>> out = new LinkedHashMap<>();
        Set<List<Integer>> used = new HashSet<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String id = String.valueOf(e.getKey());
            if (!id.matches("[a-z0-9_]+")) {
                problems.add("slots." + id + ": not a building id; skipped");
                continue;
            }
            if (!(e.getValue() instanceof List<?> list)) {
                problems.add("slots." + id + " must be a list of {at: [x, y, z], yaw: deg}");
                continue;
            }
            List<Slot> slots = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                String where = "slots." + id + "[" + i + "]";
                if (!(list.get(i) instanceof Map<?, ?> entry)) {
                    problems.add(where + " must be {at: [x, y, z], yaw: deg}");
                    continue;
                }
                int[] at = cell(entry.get("at"));
                if (at == null) {
                    problems.add(where + ".at must be [x, y, z] whole numbers");
                    continue;
                }
                if (at[0] < 0 || at[2] < 0 || at[0] >= HouseLayout.MAX_SIZE || at[2] >= HouseLayout.MAX_SIZE
                        || at[1] < MIN_Y || at[1] >= MAX_Y) {
                    problems.add(where + ".at " + at[0] + " " + at[1] + " " + at[2] + " is outside the plot");
                    continue;
                }
                Object yaw = entry.get("yaw");
                if (yaw != null && !(yaw instanceof Number)) {
                    problems.add(where + ".yaw must be a number; using 0");
                }
                if (slots.size() >= MAX_PER_TYPE) {
                    problems.add("slots." + id + ": more than " + MAX_PER_TYPE + " slots; the rest is skipped");
                    break;
                }
                if (!used.add(List.of(at[0], at[1], at[2]))) {
                    problems.add(where + ": cell " + at[0] + " " + at[1] + " " + at[2] + " is used twice; skipped");
                    continue;
                }
                slots.add(new Slot(at[0], at[1], at[2], yaw instanceof Number n ? n.floatValue() : 0f));
            }
            out.put(id, slots);
        }
        return new Result(new BuildingSlots(out), problems);
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
