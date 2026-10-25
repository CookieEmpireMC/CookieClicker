package dev.casperrs.cookieClicker.house.design;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The look of the plot fence ({@code houses/fence.yml}). Pure data and
 * parser over the YAML as plain maps:
 * <pre>
 * rail: "minecraft:spruce_fence"                        # every ring cell that is nothing else (y 0)
 * post: ["minecraft:spruce_log[axis=y]"]                # column, bottom -> top (a single string = 1 high)
 * post-spacing: [4, 3, 5]                               # gaps between posts along a side, cycled (from the
 *                                                       # side's starting corner: posts at 0, 4, 7, 12, 16, 19, ...)
 * post-every: 4                                         # old form of post-spacing: [4] (use one of the two)
 * corner: ["minecraft:stone_bricks", "minecraft:spruce_log[axis=y]"]   # column
 * topper: "minecraft:lantern"                           # optional: on top of every corner and some posts
 * topper-spacing: [2, 3]                               # gaps in POSTS between topped posts, cycled (posts 0, 2, 5,
 *                                                       # 7, 10, ... of a side); [] = none
 * topper-every: 2                                       # old form of topper-spacing: [2]; 0 = none
 * gate:
 *   width: 3                                            # opening width (the walkway), centred on the entrance
 *   left: [stone_bricks, bricks, ...]                   # pillar column, left as seen from outside
 *   right: [...]                                        # (default: same as left)
 *   arch: [null, null, null, dark_oak_planks, ...]      # per height over every opening cell; null = air
 * </pre>
 * Spec: design/fence.md. Rail connections (north/east/south/west) are computed from the neighbours.
 * Block data strings are checked against the server when placing. Every problem is collected.
 */
public record FenceStyle(List<String> post, String rail, List<Integer> postSpacing, List<String> corner,
                         @Nullable String topper, List<Integer> topperSpacing, int gateWidth, List<String> gateLeft,
                         List<String> gateRight, List<String> arch) {

    public static final int MAX_GATE_WIDTH = 15;
    public static final int MAX_ARCH = 16;
    /** Largest single gap and longest spacing list. */
    public static final int MAX_GAP = 64;
    public static final int MAX_SPACING = 32;

    public static final String AIR = "minecraft:air";

    public FenceStyle {
        postSpacing = List.copyOf(postSpacing);
        topperSpacing = List.copyOf(topperSpacing);
        if (postSpacing.isEmpty()) {
            throw new IllegalArgumentException("postSpacing needs at least one gap");
        }
        post = List.copyOf(post);
        corner = List.copyOf(corner);
        gateLeft = List.copyOf(gateLeft);
        gateRight = List.copyOf(gateRight);
        arch = List.copyOf(arch);
    }

    /** The style with even spacing: a post every {@code postEvery} cells, the topper on every {@code topperEvery}-th. */
    public FenceStyle(List<String> post, String rail, int postEvery, List<String> corner, @Nullable String topper,
                      int topperEvery, int gateWidth, List<String> gateLeft, List<String> gateRight,
                      List<String> arch) {
        this(post, rail, List.of(postEvery), corner, topper, topperEvery <= 0 ? List.of() : List.of(topperEvery),
                gateWidth, gateLeft, gateRight, arch);
    }

    /** The first post gap (the whole rhythm for an even fence). */
    public int postEvery() {
        return postSpacing.getFirst();
    }

    /** The first topper gap in posts; 0 = no toppers on posts. */
    public int topperEvery() {
        return topperSpacing.isEmpty() ? 0 : topperSpacing.getFirst();
    }

    /** Whether the cell {@code offset} cells from a side's starting corner holds a post (before corner/gate rules). */
    public boolean postAt(int offset) {
        return onCycle(postSpacing, offset);
    }

    /** How many post cells lie before {@code offset} along a side: the index of the post at {@code offset}. */
    public int postIndex(int offset) {
        int total = total(postSpacing);
        int cycles = Math.floorDiv(offset, total);
        int rest = offset - cycles * total;
        int count = cycles * postSpacing.size();
        int p = 0;
        for (int gap : postSpacing) {
            if (p >= rest) {
                break;
            }
            count++;
            p += gap;
        }
        return count;
    }

    /** Whether the post with index {@code postIndex} along a side gets the topper. */
    public boolean toppedPost(int postIndex) {
        return !topperSpacing.isEmpty() && onCycle(topperSpacing, postIndex);
    }

    /** Whether {@code n} is 0 or a running sum of the cycled {@code gaps}. */
    private static boolean onCycle(List<Integer> gaps, int n) {
        int m = Math.floorMod(n, total(gaps));
        int p = 0;
        for (int gap : gaps) {
            if (p == m) {
                return true;
            }
            p += gap;
        }
        return false;
    }

    private static int total(List<Integer> gaps) {
        int t = 0;
        for (int g : gaps) {
            t += g;
        }
        return t;
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
        List<Integer> postSpacing = spacing(yaml, "post-spacing", "post-every", 4, 1, problems);
        String topper = yaml.get("topper") == null ? null : data(yaml.get("topper"), "topper", problems);
        List<Integer> topperSpacing = spacing(yaml, "topper-spacing", "topper-every", topper == null ? 0 : 1, 0,
                problems);

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
        return new Result(new FenceStyle(post, rail, postSpacing, corner, topper, topperSpacing, width, left, right,
                arch), List.of());
    }

    /**
     * A cycled gap list ({@code listKey}: [4, 3, 5]) or its old single-number form ({@code everyKey}: 4, where
     * {@code minEvery} 0 means "none" = an empty list). Missing both = {@code fallback} (0 = empty).
     */
    private static List<Integer> spacing(Map<String, ?> yaml, String listKey, String everyKey, int fallback,
                                         int minEvery, List<String> problems) {
        Object list = yaml.get(listKey);
        if (list != null && yaml.get(everyKey) != null) {
            problems.add("use " + listKey + " or " + everyKey + ", not both");
            return List.of(Math.max(1, fallback));
        }
        if (list == null) {
            int every = whole(yaml.get(everyKey), fallback, minEvery, MAX_GAP, everyKey, problems);
            return every <= 0 ? List.of() : List.of(every);
        }
        if (!(list instanceof List<?> raw) || raw.size() > MAX_SPACING || (raw.isEmpty() && minEvery > 0)) {
            problems.add(listKey + " must be a list of up to " + MAX_SPACING + " gaps"
                    + (minEvery > 0 ? " (at least one)" : ""));
            return List.of(Math.max(1, fallback));
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            int gap = whole(raw.get(i), -1, 1, MAX_GAP, listKey + "[" + i + "]", problems);
            if (raw.get(i) == null) {
                problems.add(listKey + "[" + i + "] must be a whole number from 1 to " + MAX_GAP);
            }
            if (gap > 0) {
                out.add(gap);
            }
        }
        return out.isEmpty() && minEvery > 0 ? List.of(Math.max(1, fallback)) : out;
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
