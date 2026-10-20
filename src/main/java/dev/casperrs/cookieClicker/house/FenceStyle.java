package dev.casperrs.cookieClicker.house;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The look of the plot fence ({@code houses/fence.yml}, brief factory-makeover, Contract "fence style"). Pure data and
 * parser over the YAML as plain maps:
 * <pre>
 * post: "minecraft:spruce_fence"          # fence block data on post cells
 * rail: "minecraft:spruce_fence"          # fence block data between posts
 * post-every: 4                           # a post every n blocks along a straight side (1 = all posts)
 * corner: "minecraft:stripped_spruce_log" # block data on corner cells
 * topper: "minecraft:lantern"             # optional: block on top of a post/corner
 * topper-every: 2                         # every n-th post gets the topper (corners always do); 0 = none
 * gate:
 *   width: 3                              # opening width in blocks (the walkway)
 *   left: "minecraft:stripped_spruce_log" # pillar beside the opening, left when looking out of the plot
 *   right: "minecraft:stripped_spruce_log"
 *   arch:                                 # per height above the ground, across the opening; the pillars are as tall
 *     - "minecraft:air"                   # height 0 (walkway, cleared)
 *     - "minecraft:air"
 *     - "minecraft:spruce_slab[type=top]" # the lintel
 * </pre>
 * Fence connections (north/east/south/west) are not part of the style: the placer computes them from the neighbours.
 * Block data strings are checked against the server when placing. Every problem is collected.
 */
public record FenceStyle(String post, String rail, int postEvery, String corner, @Nullable String topper,
                         int topperEvery, int gateWidth, String gateLeft, String gateRight, List<String> arch) {

    public static final int MAX_GATE_WIDTH = 15;
    public static final int MAX_ARCH = 16;

    public FenceStyle {
        arch = List.copyOf(arch);
    }

    /** Pillar height: the arch's height, at least 1. */
    public int pillarHeight() {
        return Math.max(1, arch.size());
    }

    /** The style, or null with at least one problem. */
    public record Result(@Nullable FenceStyle style, List<String> problems) {
    }

    public static Result parse(Map<String, ?> yaml) {
        List<String> problems = new ArrayList<>();
        String post = data(yaml.get("post"), "post", problems);
        String rail = data(yaml.get("rail"), "rail", problems);
        String corner = yaml.get("corner") == null ? post : data(yaml.get("corner"), "corner", problems);
        int postEvery = whole(yaml.get("post-every"), 4, 1, 64, "post-every", problems);
        String topper = yaml.get("topper") == null ? null : data(yaml.get("topper"), "topper", problems);
        int topperEvery = whole(yaml.get("topper-every"), topper == null ? 0 : 1, 0, 64, "topper-every", problems);

        int width = 3;
        String left = null;
        String right = null;
        List<String> arch = new ArrayList<>();
        if (!(yaml.get("gate") instanceof Map<?, ?> gate)) {
            problems.add("gate must be a section with width, left, right and arch");
        } else {
            width = whole(gate.get("width"), 3, 1, MAX_GATE_WIDTH, "gate.width", problems);
            left = data(gate.get("left"), "gate.left", problems);
            right = gate.get("right") == null ? left : data(gate.get("right"), "gate.right", problems);
            if (gate.get("arch") == null) {
                arch.add("minecraft:air");
                arch.add("minecraft:air");
            } else if (!(gate.get("arch") instanceof List<?> raw) || raw.isEmpty() || raw.size() > MAX_ARCH) {
                problems.add("gate.arch must be a list of 1 to " + MAX_ARCH + " block data strings (bottom first)");
            } else {
                for (int i = 0; i < raw.size(); i++) {
                    String d = data(raw.get(i), "gate.arch[" + i + "]", problems);
                    if (d != null) {
                        arch.add(d);
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            return new Result(null, problems);
        }
        return new Result(new FenceStyle(post, rail, postEvery, corner, topper, topperEvery, width, left, right, arch),
                List.of());
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
