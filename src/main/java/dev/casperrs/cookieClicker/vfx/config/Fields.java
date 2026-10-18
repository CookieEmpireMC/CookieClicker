package dev.casperrs.cookieClicker.vfx.config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Color;
import org.jspecify.annotations.Nullable;

/**
 * Typed, range-checked reads from one YAML map (a layer or a step). Bad values fall back to the default and are
 * reported; unknown keys are reported too (catches typos such as {@code radious}).
 */
final class Fields {

    private final Map<?, ?> map;
    private final String where;
    private final List<String> problems;
    private final Set<String> used = new HashSet<>();
    private boolean fatal;

    Fields(Map<?, ?> map, String where, List<String> problems) {
        this.map = map;
        this.where = where;
        this.problems = problems;
    }

    /** Fields for a nested map (e.g. a pop step), reporting into the same problem list. */
    Fields child(Map<?, ?> nested, String name) {
        return new Fields(nested, where + " " + name, problems);
    }

    /** True once a required field was missing or invalid: the caller skips the layer. */
    boolean fatal() {
        return fatal;
    }

    void problem(String message) {
        problems.add(where + ": " + message);
    }

    void fail(String message) {
        problem(message + " - layer skipped");
        fatal = true;
    }

    boolean has(String key) {
        return map.containsKey(key);
    }

    @Nullable Object raw(String key) {
        used.add(key);
        return map.get(key);
    }

    @Nullable String string(String key) {
        Object v = raw(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Map<?, ?> || v instanceof List<?>) {
            problem("'" + key + "' must be text, got " + v);
            return null;
        }
        return v.toString();
    }

    String requiredString(String key) {
        String v = string(key);
        if (v == null || v.isBlank()) {
            fail("missing '" + key + "'");
            return "";
        }
        return v;
    }

    double number(String key, double def, double min, double max) {
        Object v = raw(key);
        if (v == null) {
            return def;
        }
        if (!(v instanceof Number n) || Double.isNaN(n.doubleValue()) || Double.isInfinite(n.doubleValue())) {
            problem("'" + key + "' must be a number, got '" + v + "'; using " + def);
            return def;
        }
        double d = n.doubleValue();
        if (d < min || d > max) {
            double c = Math.max(min, Math.min(max, d));
            problem("'" + key + "' = " + d + " is outside " + min + ".." + max + "; using " + c);
            return c;
        }
        return d;
    }

    int integer(String key, int def, int min, int max) {
        Object v = raw(key);
        if (v != null && !(v instanceof Integer || v instanceof Long)) {
            problem("'" + key + "' must be a whole number, got '" + v + "'; using " + def);
            return def;
        }
        return (int) Math.round(number(key, def, min, max));
    }

    boolean bool(String key, boolean def) {
        Object v = raw(key);
        if (v == null) {
            return def;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        problem("'" + key + "' must be true or false, got '" + v + "'; using " + def);
        return def;
    }

    /** A list of three numbers, e.g. {@code [0, 0.5, 0]}. */
    double[] vec3(String key, double[] def) {
        Object v = raw(key);
        if (v == null) {
            return def;
        }
        if (v instanceof List<?> list && list.size() == 3 && list.stream().allMatch(e -> e instanceof Number)) {
            return new double[] {((Number) list.get(0)).doubleValue(), ((Number) list.get(1)).doubleValue(),
                    ((Number) list.get(2)).doubleValue()};
        }
        problem("'" + key + "' must be a list of 3 numbers like [0, 0.5, 0], got '" + v + "'");
        return def;
    }

    /** {@code "#rrggbb"} (opaque) or {@code "#aarrggbb"}. */
    @Nullable Color color(String key, @Nullable Color def) {
        String v = string(key);
        if (v == null) {
            return def;
        }
        Color c = parseColor(v);
        if (c == null) {
            problem("'" + key + "' must be a colour like \"#f5c542\" or \"#80000000\", got '" + v + "'");
            return def;
        }
        return c;
    }

    static @Nullable Color parseColor(String v) {
        String s = v.trim();
        if (!s.startsWith("#") || (s.length() != 7 && s.length() != 9)) {
            return null;
        }
        try {
            long n = Long.parseLong(s.substring(1), 16);
            return s.length() == 7 ? Color.fromRGB((int) n) : Color.fromARGB((int) n);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Reports every key that no read touched. Call after reading all fields. */
    void reportUnknown() {
        for (Object k : map.keySet()) {
            String key = String.valueOf(k);
            if (!used.contains(key)) {
                problem("unknown field '" + key + "' ignored");
            }
        }
    }

    static String lower(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
