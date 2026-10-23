package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The conveyor belt frame ({@code houses/belt.yml}, spec design/conveyor-belt.md). Pure data
 * and parser over the YAML as plain maps:
 * <pre>
 * direction: north                 # the belt runs this way; block states are written for it (only north)
 * surface-height: 1                # frame column height; the moving surface rests on top of it
 * item-model: "cookieclicker:conveyor_belt"
 * columns:                         # named columns, block data bottom -> top, length = surface-height
 *   roller: ["minecraft:spruce_log[axis=x]"]
 *   housing: ["minecraft:waxed_copper_grate[waterlogged=false]"]
 * first: roller                    # cell 0 (layout.belt.from, at the hatch)
 * last: roller                     # cell n-1 (layout.belt.to, next to the pedestal)
 * middle: [housing, roller]        # cell i in between: middle[(i - 1) % len(middle)]
 * </pre>
 * Only the belt cells change, from y 0 to surface-height - 1. Block data strings are checked against the server
 * when placing. Every problem is collected.
 *
 * @param surfaceHeight frame height in blocks (1 to {@link #MAX_HEIGHT}); the belt surface sits on top
 * @param columns       the named columns, each exactly {@code surfaceHeight} long
 */
public record BeltStyle(int surfaceHeight, Map<String, List<String>> columns, String first, String last,
                        List<String> middle) {

    public static final int MAX_HEIGHT = 4;
    public static final int DEFAULT_HEIGHT = 1;

    public BeltStyle {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        columns.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        columns = java.util.Collections.unmodifiableMap(copy);
        middle = List.copyOf(middle);
    }

    /** The column of cell {@code i} of a belt of {@code n} cells (bottom -> top); empty when there is no frame. */
    public List<String> column(int i, int n) {
        String name = i == 0 ? first : i == n - 1 ? last : middle.isEmpty() ? first : middle.get((i - 1) % middle.size());
        return columns.getOrDefault(name, List.of());
    }

    /** Every block data string the style uses. */
    public List<String> allBlockData() {
        List<String> out = new ArrayList<>();
        columns.values().forEach(out::addAll);
        return out;
    }

    /** No frame blocks at the default height: used when {@code belt.yml} is missing or broken. */
    public static BeltStyle none() {
        return none(DEFAULT_HEIGHT);
    }

    /** No frame blocks at this height (the surface still sits where the file says). */
    public static BeltStyle none(int height) {
        return new BeltStyle(height, Map.of("none", List.of()), "none", "none", List.of());
    }

    /** The style, or null with at least one problem. */
    public record Result(@Nullable BeltStyle style, List<String> problems) {
    }

    public static Result parse(Map<String, ?> yaml) {
        List<String> problems = new ArrayList<>();
        Object direction = yaml.get("direction");
        if (direction != null && !"north".equals(String.valueOf(direction).trim())) {
            problems.add("direction must be north (straight belts toward -Z only), got " + direction);
        }
        int height = DEFAULT_HEIGHT;
        Object rawHeight = yaml.get("surface-height");
        if (rawHeight != null) {
            if (rawHeight instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                    && n.intValue() >= 1 && n.intValue() <= MAX_HEIGHT) {
                height = n.intValue();
            } else {
                problems.add("surface-height must be a whole number from 1 to " + MAX_HEIGHT);
            }
        }
        Map<String, List<String>> columns = new LinkedHashMap<>();
        if (!(yaml.get("columns") instanceof Map<?, ?> cols) || cols.isEmpty()) {
            problems.add("columns must be a section of named block data lists");
        } else {
            for (Map.Entry<?, ?> e : cols.entrySet()) {
                String name = String.valueOf(e.getKey());
                List<String> column = new ArrayList<>();
                if (!(e.getValue() instanceof List<?> list) || list.size() != height) {
                    problems.add("columns." + name + " must list exactly surface-height (" + height
                            + ") block data strings, bottom first");
                    continue;
                }
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof String s && !s.isBlank()) {
                        column.add(s.trim());
                    } else {
                        problems.add("columns." + name + "[" + i + "] needs block data like minecraft:spruce_log");
                    }
                }
                columns.put(name, column);
            }
        }
        String first = name(yaml.get("first"), "first", columns, problems);
        String last = name(yaml.get("last"), "last", columns, problems);
        List<String> middle = new ArrayList<>();
        Object rawMiddle = yaml.get("middle");
        if (rawMiddle instanceof String) {
            middle.add(name(rawMiddle, "middle", columns, problems));
        } else if (rawMiddle instanceof List<?> list && !list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                middle.add(name(list.get(i), "middle[" + i + "]", columns, problems));
            }
        } else {
            problems.add("middle must be a column name or a list of column names");
        }
        if (!problems.isEmpty()) {
            return new Result(null, problems);
        }
        return new Result(new BeltStyle(height, columns, first, last, middle), List.of());
    }

    private static String name(@Nullable Object raw, String key, Map<String, List<String>> columns,
                               List<String> problems) {
        String name = raw == null ? "" : String.valueOf(raw).trim();
        if (!columns.containsKey(name)) {
            problems.add(key + " must name one of the columns " + columns.keySet() + ", got '" + name + "'");
        }
        return name;
    }

    /** The surface height as a double, for {@code Plot.Belt}. */
    public double surface() {
        return surfaceHeight;
    }
}
