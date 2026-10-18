package dev.casperrs.cookieClicker.vfx.config;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig.Limits;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfig.Preset;
import dev.casperrs.cookieClicker.vfx.shape.DisplayTrack;
import dev.casperrs.cookieClicker.vfx.shape.Motions;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import dev.casperrs.cookieClicker.vfx.shape.ShapeKind;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams.Direction;
import dev.casperrs.cookieClicker.vfx.shape.ShapeParams.Plane;
import dev.casperrs.cookieClicker.vfx.shape.Shapes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Parses {@code effects.yml} into an {@link EffectsConfig}. Never throws on bad content: every problem is collected
 * (the caller logs them) and the bad layer or preset is skipped. Needs no running server, so it is unit-tested and
 * used by the preview export. Item, block and model names are only checked for syntax here; {@code Effects.load()}
 * resolves them against the server.
 */
public final class EffectsConfigParser {

    public static final String LIMITS = "limits";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final double[] ZERO = {0, 0, 0};

    private EffectsConfigParser() {
    }

    /**
     * @param root     the server's effects.yml
     * @param defaults the effects.yml shipped in the jar, or null; presets missing from {@code root} are taken from
     *                 here, and so are missing limit values, so new effects in a plugin update work with an old file
     */
    public static EffectsConfig parse(ConfigurationSection root, @Nullable ConfigurationSection defaults) {
        List<String> problems = new ArrayList<>();
        Limits fallback = defaults == null ? Limits.DEFAULTS : limits(defaults, Limits.DEFAULTS, new ArrayList<>());
        Limits limits = limits(root, fallback, problems);
        Map<String, Preset> presets = new LinkedHashMap<>();
        if (defaults != null) {
            presets.putAll(presets(defaults, limits, new ArrayList<>()));
        }
        presets.putAll(presets(root, limits, problems));
        return new EffectsConfig(limits, Map.copyOf(presets), List.copyOf(problems));
    }

    private static Limits limits(ConfigurationSection root, Limits def, List<String> problems) {
        ConfigurationSection s = root.getConfigurationSection(LIMITS);
        if (s == null) {
            return def;
        }
        Fields f = new Fields(s.getValues(false), "limits", problems);
        Limits l = new Limits(
                f.integer("max-particles-per-effect", def.maxParticlesPerEffect(), 1, 2000),
                f.integer("max-display-entities-per-player", def.maxDisplayEntitiesPerPlayer(), 0, 64),
                f.integer("max-display-entities-global", def.maxDisplayEntitiesGlobal(), 0, 2000));
        f.reportUnknown();
        return l;
    }

    private static Map<String, Preset> presets(ConfigurationSection root, Limits limits, List<String> problems) {
        Map<String, Preset> out = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            if (id.equals(LIMITS)) {
                continue;
            }
            if (!ID.matcher(id).matches()) {
                problems.add("'" + id + "': effect IDs use only a-z, 0-9 and _ - effect skipped");
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null || !s.isList("layers")) {
                problems.add(id + ": needs a 'layers' list - effect skipped");
                continue;
            }
            for (String key : s.getKeys(false)) {
                if (!key.equals("layers")) {
                    problems.add(id + ": unknown field '" + key + "' ignored");
                }
            }
            List<LayerSpec> layers = new ArrayList<>();
            List<Map<?, ?>> raw = s.getMapList("layers");
            if (raw.size() != s.getList("layers", List.of()).size()) {
                problems.add(id + ": every layer must be a map like { type: particle, ... } - non-maps skipped");
            }
            for (int i = 0; i < raw.size(); i++) {
                LayerSpec layer = layer(id, i, raw.get(i), problems);
                if (layer != null) {
                    layers.add(layer);
                }
            }
            layers = enforceParticleCap(id, layers, limits, problems);
            int entities = layers.stream().mapToInt(LayerSpec::entityCost).sum();
            if (entities > limits.maxDisplayEntitiesPerPlayer()) {
                problems.add(id + ": spawns " + entities + " display entities per play but "
                        + "max-display-entities-per-player is " + limits.maxDisplayEntitiesPerPlayer()
                        + "; some layers will be dropped at play time");
            }
            if (layers.isEmpty()) {
                problems.add(id + ": no valid layers - effect skipped");
                continue;
            }
            out.put(id, new Preset(id, List.copyOf(layers)));
        }
        return out;
    }

    static @Nullable LayerSpec layer(String id, int index, Map<?, ?> map, List<String> problems) {
        Object typeRaw = map.get("type");
        String type = typeRaw == null ? "" : Fields.lower(typeRaw.toString());
        String label = index + ":" + type;
        Fields f = new Fields(map, id + " layer " + index + " (" + (type.isEmpty() ? "?" : type) + ")", problems);
        f.raw("type");
        LayerSpec spec = switch (type) {
            case "particle" -> particle(label, f);
            case "floating-text" -> floatingText(label, f);
            case "item-crumbs" -> itemCrumbs(label, f);
            case "scale-pop" -> scalePop(label, f);
            case "" -> {
                f.fail("missing 'type' (particle, floating-text, item-crumbs or scale-pop)");
                yield null;
            }
            default -> {
                f.fail("unknown type '" + type + "' (use particle, floating-text, item-crumbs or scale-pop)");
                yield null;
            }
        };
        if (spec == null || f.fatal()) {
            return null;
        }
        f.reportUnknown();
        return spec;
    }

    // ---- particle -----------------------------------------------------------------------------------------

    private static @Nullable LayerSpec particle(String label, Fields f) {
        String name = f.requiredString("particle");
        Particle particle = name.isEmpty() ? null : particleByKey(name);
        if (particle == null) {
            if (!name.isEmpty()) {
                f.fail("unknown particle '" + name + "' (use a 26.3 particle key like dust, item, end_rod)");
            }
            return null;
        }
        LayerSpec.ParticleData data = particleData(particle, f);
        if (data == null) {
            return null;
        }
        String shapeName = f.string("shape");
        ShapeKind kind = shapeName == null ? ShapeKind.POINT : ShapeKind.fromConfig(shapeName);
        if (kind == null) {
            f.fail("unknown shape '" + shapeName + "' (point, burst, sphere, ring, arc, line, helix, fountain, sparkles)");
            return null;
        }
        ShapeParams d = ShapeParams.defaults(kind);
        double radius = f.number("radius", d.radius(), 0, 8);
        double[] to = f.vec3("to", new double[] {d.toX(), d.toY(), d.toZ()});
        Plane plane = enumValue(f, "plane", Plane.class, d.plane());
        Direction direction = enumValue(f, "direction", Direction.class, d.direction());
        ShapeParams shape = new ShapeParams(kind,
                f.integer("points", d.points(), 1, 500),
                radius,
                f.number("radius-start", radius, 0, 8),
                f.number("height", d.height(), -8, 8),
                f.number("turns", d.turns(), 0, 16),
                f.number("angle", d.angleDeg(), 0, 360),
                f.number("start-angle", d.startAngleDeg(), -360, 360),
                f.number("spread", d.spreadDeg(), 0, 180),
                f.number("up-bias", d.upBias(), 0, 1),
                to[0], to[1], to[2], plane,
                f.integer("duration-ticks", d.durationTicks(), 1, 200),
                f.integer("stagger-ticks", d.staggerTicks(), 0, 100),
                f.integer("emit-every", d.emitEvery(), 1, 20),
                direction,
                f.integer("strands", d.strands(), 1, 8));
        double[] o = f.vec3("origin", ZERO);
        return new LayerSpec.Particles(label,
                f.integer("delay-ticks", 0, 0, 200), o[0], o[1], o[2],
                particle, data, shape, Shapes.build(shape),
                f.integer("count", 1, 1, 100),
                f.number("offset", 0, 0, 4),
                f.number("speed", 0, 0, 4));
    }

    /** Resolves {@code dust} or {@code minecraft:dust} against the 26.3 {@link Particle} enum. */
    public static @Nullable Particle particleByKey(String name) {
        String key = Fields.lower(name);
        if (key.startsWith("minecraft:")) {
            key = key.substring("minecraft:".length());
        }
        for (Particle p : Particle.values()) {
            if (p.getKey().getKey().equals(key)) {
                return p;
            }
        }
        return null;
    }

    private static LayerSpec.@Nullable ParticleData particleData(Particle particle, Fields f) {
        Class<?> type = particle.getDataType();
        Color white = Color.WHITE;
        if (type == Void.class) {
            return new LayerSpec.ParticleData(type, null, null, 0, 0, null, null, null, 0);
        }
        if (type == Particle.DustOptions.class) {
            return new LayerSpec.ParticleData(type, f.color("color", white), null,
                    (float) f.number("size", 1, 0.01, 4), 0, null, null, null, 0);
        }
        if (type == Particle.DustTransition.class) {
            Color from = f.color("color", white);
            return new LayerSpec.ParticleData(type, from, f.color("to-color", from),
                    (float) f.number("size", 1, 0.01, 4), 0, null, null, null, 0);
        }
        if (type == Color.class) {
            return new LayerSpec.ParticleData(type, f.color("color", white), null, 0, 0, null, null, null, 0);
        }
        if (type == Particle.Spell.class) {
            return new LayerSpec.ParticleData(type, f.color("color", white), null, 0,
                    (float) f.number("power", 1, 0, 10), null, null, null, 0);
        }
        if (type == ItemStack.class) {
            String item = material(f, "item", "cookie");
            String model = itemModel(f);
            return item == null ? null : new LayerSpec.ParticleData(type, null, null, 0, 0, item, model, null, 0);
        }
        if (type == BlockData.class) {
            String block = material(f, "block", null);
            return block == null ? null : new LayerSpec.ParticleData(type, null, null, 0, 0, null, null, block, 0);
        }
        if (type == Float.class || type == Integer.class) {
            return new LayerSpec.ParticleData(type, null, null, 0, 0, null, null, null,
                    (float) f.number("value", type == Float.class ? 1 : 0, 0, 1000));
        }
        f.fail("particle '" + particle.getKey().getKey() + "' needs " + type.getSimpleName()
                + " data, which effects.yml doesn't support");
        return null;
    }

    /** A material name (e.g. {@code cookie}); {@code def == null} makes it required. */
    private static @Nullable String material(Fields f, String key, @Nullable String def) {
        String name = f.string(key);
        if (name == null) {
            if (def == null) {
                f.fail("missing '" + key + "'");
            }
            return def;
        }
        if (Material.matchMaterial(name) == null) {
            f.fail("unknown " + key + " '" + name + "'");
            return null;
        }
        return name;
    }

    /** Optional {@code item-model: namespace:path} (syntax only; the pack decides if it exists). */
    private static @Nullable String itemModel(Fields f) {
        String model = f.string("item-model");
        if (model != null && NamespacedKey.fromString(Fields.lower(model)) == null) {
            f.problem("'item-model' '" + model + "' is not a valid key like cookieclicker:cookie; ignored");
            return null;
        }
        return model == null ? null : Fields.lower(model);
    }

    /**
     * Keeps the preset under {@code max-particles-per-effect}: thins every particle layer by the same factor
     * (fewer points), then drops trailing particle layers if that isn't enough. Done once at load.
     */
    private static List<LayerSpec> enforceParticleCap(String id, List<LayerSpec> layers, Limits limits,
                                                     List<String> problems) {
        int cap = limits.maxParticlesPerEffect();
        int total = layers.stream().mapToInt(LayerSpec::particleCost).sum();
        if (total <= cap) {
            return layers;
        }
        problems.add(id + ": " + total + " particles per play is over max-particles-per-effect (" + cap
                + "); thinning the particle layers");
        List<LayerSpec> out = new ArrayList<>(layers);
        for (int round = 0; round < 8 && total > cap; round++) {
            double factor = (double) cap / total;
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i) instanceof LayerSpec.Particles p) {
                    out.set(i, thin(p, factor));
                }
            }
            total = out.stream().mapToInt(LayerSpec::particleCost).sum();
        }
        for (int i = out.size() - 1; i >= 0 && total > cap; i--) {
            if (out.get(i) instanceof LayerSpec.Particles p) {
                problems.add(id + ": dropped " + p.label() + " to stay under the particle cap");
                out.remove(i);
                total -= p.particleCost();
            }
        }
        return out;
    }

    private static LayerSpec.Particles thin(LayerSpec.Particles p, double factor) {
        if (p.shape().kind() == ShapeKind.POINT || p.count() > 1) {
            int count = Math.max(1, (int) Math.floor(p.count() * factor));
            if (count < p.count()) {
                return new LayerSpec.Particles(p.label(), p.delayTicks(), p.ox(), p.oy(), p.oz(), p.particle(),
                        p.data(), p.shape(), p.plan(), count, p.offset(), p.speed());
            }
        }
        ShapeParams s = p.shape().withPoints(Math.max(1, (int) Math.floor(p.shape().points() * factor)));
        return new LayerSpec.Particles(p.label(), p.delayTicks(), p.ox(), p.oy(), p.oz(), p.particle(), p.data(),
                s, Shapes.build(s), p.count(), p.offset(), p.speed());
    }

    // ---- floating text ------------------------------------------------------------------------------------

    private static @Nullable LayerSpec floatingText(String label, Fields f) {
        String text = f.requiredString("text");
        int startTick = f.integer("start-tick", 1, 1, 5);
        int popIn = f.integer("pop-in-ticks", 3, 0, 10);
        int fade = f.integer("fade-ticks", 6, 0, 40);
        int min = Motions.minTextDuration(popIn, fade, startTick);
        int duration = f.integer("duration-ticks", 20, 1, 200);
        if (duration < min) {
            f.problem("'duration-ticks' " + duration + " is too short for pop-in + fade; using " + min);
            duration = min;
        }
        double scale = f.number("scale", 1, 0.05, 10);
        DisplayTrack track = Motions.floatingText(
                f.number("rise", 0.8, -4, 4), duration, scale,
                f.number("pop-in-scale", 0.5, 0.05, 2),
                f.number("overshoot", 1.15, 0.5, 2),
                popIn, fade, startTick);
        double[] o = f.vec3("origin", ZERO);
        Display.Billboard billboard = enumValue(f, "billboard", Display.Billboard.class, Display.Billboard.CENTER);
        Color background = f.color("background", Color.fromARGB(0, 0, 0, 0));
        LayerSpec spec = new LayerSpec.FloatingText(label, f.integer("delay-ticks", 0, 0, 200), o[0], o[1], o[2],
                text, track, f.number("spread", 0.2, 0, 2), f.bool("shadow", true), f.bool("see-through", false),
                background == null ? Color.fromARGB(0, 0, 0, 0) : background, billboard,
                (float) f.number("view-range", 0.5, 0.05, 4));
        return f.fatal() ? null : spec;
    }

    // ---- item crumbs --------------------------------------------------------------------------------------

    private static @Nullable LayerSpec itemCrumbs(String label, Fields f) {
        String item = material(f, "item", "cookie");
        String model = itemModel(f);
        int count = f.integer("count", 4, 1, 16);
        double distance = f.number("distance", 0.7, 0, 4);
        double spread = f.number("spread", 60, 0, 180);
        double lift = f.number("lift", 0.2, 0, 2);
        double drop = f.number("drop", 0.5, 0, 4);
        int start = f.integer("start-tick", 1, 1, 5);
        int duration = f.integer("duration-ticks", 14, start + 3, 200);
        double scale = f.number("scale", 0.2, 0.02, 2);
        double spin = f.number("spin", 150, 0, 200);
        List<DisplayTrack> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tracks.add(Motions.crumb(i, count, distance, spread, lift, drop, duration, scale, spin, start));
        }
        double[] o = f.vec3("origin", ZERO);
        int delay = f.integer("delay-ticks", 0, 0, 200);
        float range = (float) f.number("view-range", 0.5, 0.05, 4);
        return item == null ? null
                : new LayerSpec.ItemCrumbs(label, delay, o[0], o[1], o[2], item, model, List.copyOf(tracks), range);
    }

    // ---- scale pop ----------------------------------------------------------------------------------------

    private static @Nullable LayerSpec scalePop(String label, Fields f) {
        Object raw = f.raw("steps");
        if (raw == null) {
            return new LayerSpec.ScalePop(label, PopSequence.standard());
        }
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            f.fail("'steps' must be a list like [{ scale: [1.25, 0.75, 1.25], ticks: 2 }, ...]");
            return null;
        }
        List<PopSequence.Step> steps = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof Map<?, ?> m)) {
                f.fail("step " + i + " must be a map like { scale: [1.25, 0.75, 1.25], ticks: 2 }");
                return null;
            }
            Fields s = f.child(m, "step " + i);
            double[] scale = s.raw("scale") instanceof Number n
                    ? new double[] {n.doubleValue(), n.doubleValue(), n.doubleValue()}
                    : s.vec3("scale", new double[] {1, 1, 1});
            int ticks = s.integer("ticks", 2, 1, 20);
            boolean ok = true;
            for (double v : scale) {
                ok &= v > 0 && v <= 4;
            }
            if (!ok) {
                f.fail("step " + i + " scale must be between 0 and 4");
                return null;
            }
            s.reportUnknown();
            steps.add(new PopSequence.Step(scale[0], scale[1], scale[2], ticks));
        }
        return new LayerSpec.ScalePop(label, new PopSequence(steps));
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private static <E extends Enum<E>> E enumValue(Fields f, String key, Class<E> type, E def) {
        String v = f.string(key);
        if (v == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            f.problem("'" + key + "' '" + v + "' is not one of "
                    + java.util.Arrays.toString(type.getEnumConstants()).toLowerCase(Locale.ROOT) + "; using "
                    + def.name().toLowerCase(Locale.ROOT));
            return def;
        }
    }
}
