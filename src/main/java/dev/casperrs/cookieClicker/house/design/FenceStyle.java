package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The look of the plot fence ({@code houses/fence.yml}, brief factory-makeover, Contract "fence style"). Pure data and
 * parser over the YAML as plain maps:
 * <pre>
 * rail: "minecraft:spruce_fence"                        # every ring cell that is nothing else (y 0)
 * post: ["minecraft:spruce_log[axis=y]"]                # column, bottom -> top (a single string = 1 high)
 * post-every: 4                                         # a post every n cells along a side
 * corner: ["minecraft:stone_bricks", "minecraft:spruce_log[axis=y]"]   # column
 * topper: "minecraft:lantern"                           # optional: on top of every corner and some posts
 * topper-every: 2                                       # every n-th post gets the topper; 0 = none
 * gate:
 *   width: 3                                            # opening width (the walkway), centred on the entrance
 *   left: [stone_bricks, bricks, ...]                   # pillar column, left as seen from outside
 *   right: [...]                                        # (default: same as left)
 *   arch: [null, null, null, dark_oak_planks, ...]      # per height over every opening cell; null = air
 * </pre>
 * Spec: design/fence.md. Rail connections (north/east/south/west) are computed from the neighbours.
 * Block data strings are checked against the server when placing. Every problem is collected.
 */
public record FenceStyle(List<String> post, String rail, int postEvery, List<String> corner, @Nullable String topper,
                         int topperEvery, int gateWidth, List<String> gateLeft, List<String> gateRight,
                         List<String> arch) {

    public static final int MAX_GATE_WIDTH = 15;
    public static final int MAX_ARCH = 16;

    public static final String AIR = "minecraft:air";

    public FenceStyle {
        post = List.copyOf(post);
        corner = List.copyOf(corner);
        gateLeft = List.copyOf(gateLeft);
        gateRight = List.copyOf(gateRight);
        arch = List.copyOf(arch);
    }

    /** Every block data string the style uses. */
    public List<String> allBlockData() {
        List<String> out = new ArrayList<>(post);
        out.add(rail);
        out.addAll(corner);
        out.addAll(gateLeft);
        out.addAll(gateRight);
        out.addAll(arch);
        if (topper != null) {
            out.add(topper);
        }
        return out;
    }

    /** The style, or null with at least one problem. */
    public record Result(@Nullable FenceStyle style, List<String> problems) {
    }

    public static Result parse(Map<String, ?> yaml) {
        List<String> problems = new ArrayList<>();
        List<String> post = column(yaml.get("post"), "post", false, problems);
        String rail = data(yaml.get("rail"), "rail", problems);
        List<String> corner = yaml.get("corner") == null ? post : column(yaml.get("corner"), "corner", false, problems);
        int postEvery = whole(yaml.get("post-every"), 4, 1, 64, "post-every", problems);
        String topper = yaml.get("topper") == null ? null : data(yaml.get("topper"), "topper", problems);
        int topperEvery = whole(yaml.get("topper-every"), topper == null ? 0 : 1, 0, 64, "topper-every", problems);

        int width = 3;
        List<String> left = List.of();
        List<String> right = List.of();
        List<String> arch = List.of(AIR, AIR);
        if (!(yaml.get("gate") instanceof Map<?, ?> gate)) {
            problems.add("gate must be a section with width, left, right and arch");
        } else {
            width = whole(gate.get("width"), 3, 1, MAX_GATE_WIDTH, "gate.width", problems);
            left = column(gate.get("left"), "gate.left", false, problems);
            right = gate.get("right") == null ? left : column(gate.get("right"), "gate.right", false, problems);
            if (gate.get("arch") != null) {
                arch = column(gate.get("arch"), "gate.arch", true, problems);
            }
        }
        if (!problems.isEmpty()) {
            return new Result(null, problems);
        }
        return new Result(new FenceStyle(post, rail, postEvery, corner, topper, topperEvery, width, left, right, arch),
                List.of());
    }

    /** A column (list bottom -> top, or one string); {@code nullIsAir}: null entries mean air. */
    private static List<String> column(@Nullable Object raw, String key, boolean nullIsAir, List<String> problems) {
        if (raw instanceof String) {
            String d = data(raw, key, problems);
            return d == null ? List.of() : List.of(d);
        }
        if (!(raw instanceof List<?> list) || list.isEmpty() || list.size() > MAX_ARCH) {
            problems.add(key + " must be a list of 1 to " + MAX_ARCH + " block data strings (bottom first)");
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null && nullIsAir) {
                out.add(AIR);
                continue;
            }
            String d = data(list.get(i), key + "[" + i + "]", problems);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    private static @Nullable String data(@Nullable Object raw, String key, List<String> problems) {
        if (!(raw instanceof String s) || s.isBlank()) {
            problems.add(key + " needs block data like minecraft:spruce_fence");
            return null;
        }
        return s.trim();
    }

    private static int whole(@Nullable Object raw, int fallback, int min, int max, String key, List<String> problems) {
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())
                || n.intValue() < min || n.intValue() > max) {
            problems.add(key + " must be a whole number from " + min + " to " + max);
            return fallback;
        }
        return n.intValue();
    }
}
