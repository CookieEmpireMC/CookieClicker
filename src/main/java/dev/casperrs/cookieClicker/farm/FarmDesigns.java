package dev.casperrs.cookieClicker.farm;

import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseDesignParser;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The countryside and its place files, parsed and checked against each other. Pure. {@code houses/countryside.yml} is a
 * house design (ground-depth up to {@value HouseDesignParser#MAX_FARM_GROUND_DEPTH}) plus
 * <pre>
 * places:
 *   mill: {at: [x, y, z]}   # where the place file's cell [0, 0, 0] (first layer above its ground) lands, in
 *                           # countryside design cells; places paste after the countryside, unrotated, and win
 * </pre>
 * Each place is {@code houses/<place>.yml}: a house design with {@link PlacePoints}.
 */
public final class FarmDesigns {

    public static final String COUNTRYSIDE = "countryside";
    private static final Pattern PLACE_NAME = Pattern.compile("[a-z0-9_]{1,32}");

    /** A parsed place file. */
    public record Place(String name, HouseDesign design, PlacePoints points) {
    }

    /** The countryside with its places in file order ({@code at} = countryside design cell). */
    public record Layout(HouseDesign countryside, Map<String, PlacePoints.Cell> placeAt, Map<String, Place> places) {
        public Layout {
            placeAt = Collections.unmodifiableMap(new LinkedHashMap<>(placeAt));
            places = Collections.unmodifiableMap(new LinkedHashMap<>(places));
        }
    }

    /**
     * The layout, or a problem: {@code file} names the design the problem is in, {@code missing} is true when that
     * file does not exist.
     */
    public record Result(@Nullable Layout layout, String file, boolean missing, List<String> problems) {
        static Result failed(String file, boolean missing, List<String> problems) {
            return new Result(null, file, missing, List.copyOf(problems));
        }
    }

    private FarmDesigns() {
    }

    /**
     * {@code yamlOf} returns a design file's YAML as plain maps (null = no such file) and throws
     * IllegalArgumentException with the reason when the file is no valid YAML.
     */
    public static Result load(Function<String, @Nullable Map<String, Object>> yamlOf) {
        Map<String, Object> root;
        try {
            root = yamlOf.apply(COUNTRYSIDE);
        } catch (IllegalArgumentException e) {
            return Result.failed(COUNTRYSIDE, false, List.of(String.valueOf(e.getMessage())));
        }
        if (root == null) {
            return Result.failed(COUNTRYSIDE, true, List.of());
        }
        HouseDesignParser.Result parsed = HouseDesignParser.parse(COUNTRYSIDE, root,
                HouseDesignParser.MAX_FARM_GROUND_DEPTH);
        List<String> problems = new ArrayList<>(parsed.problems());
        Map<String, PlacePoints.Cell> at = placeAt(root.get("places"), parsed.design(), problems);
        if (parsed.design() == null || !problems.isEmpty()) {
            return Result.failed(COUNTRYSIDE, false, problems);
        }
        Map<String, Place> places = new LinkedHashMap<>();
        for (String name : at.keySet()) {
            Map<String, Object> yaml;
            try {
                yaml = yamlOf.apply(name);
            } catch (IllegalArgumentException e) {
                return Result.failed(name, false, List.of(String.valueOf(e.getMessage())));
            }
            if (yaml == null) {
                return Result.failed(name, true, List.of());
            }
            HouseDesignParser.Result place = HouseDesignParser.parse(name, yaml,
                    HouseDesignParser.MAX_FARM_GROUND_DEPTH);
            if (place.design() == null) {
                return Result.failed(name, false, place.problems());
            }
            List<String> pointProblems = new ArrayList<>();
            PlacePoints points = PlacePoints.parse(place.design(), yaml.get("points"), pointProblems);
            if (points == null) {
                return Result.failed(name, false, pointProblems);
            }
            places.put(name, new Place(name, place.design(), points));
        }
        return new Result(new Layout(parsed.design(), at, places), COUNTRYSIDE, false, List.of());
    }

    private static Map<String, PlacePoints.Cell> placeAt(@Nullable Object raw, @Nullable HouseDesign countryside,
                                                         List<String> problems) {
        Map<String, PlacePoints.Cell> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> places) || places.isEmpty()) {
            problems.add("places must map each place name to {at: [x, y, z]}");
            return out;
        }
        for (Map.Entry<?, ?> e : places.entrySet()) {
            String name = String.valueOf(e.getKey());
            String where = "places." + name;
            if (!PLACE_NAME.matcher(name).matches() || name.equals(COUNTRYSIDE)) {
                problems.add(where + ": place names use a-z, 0-9 and _ (the place file houses/<name>.yml)");
                continue;
            }
            if (!(e.getValue() instanceof Map<?, ?> spec)) {
                problems.add(where + " must be {at: [x, y, z]}");
                continue;
            }
            if (spec.get("yaw") instanceof Number yaw && yaw.doubleValue() != 0) {
                problems.add(where + ".yaw: places are pasted unrotated; generate the file in its final orientation");
                continue;
            }
            int[] at = HouseDesignParser.ints(spec.get("at"), 3);
            if (at == null) {
                problems.add(where + ".at must be [x, y, z] whole numbers");
            } else if (countryside != null
                    && !HouseDesignParser.inside(at, countryside.size(), countryside.groundDepth())) {
                problems.add(where + ".at " + Arrays.toString(at) + " is outside the countryside size "
                        + Arrays.toString(countryside.size()));
            } else {
                out.put(name, new PlacePoints.Cell(at[0], at[1], at[2]));
            }
        }
        return out;
    }
}
