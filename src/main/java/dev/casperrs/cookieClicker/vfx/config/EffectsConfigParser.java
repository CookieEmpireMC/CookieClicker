package dev.casperrs.cookieClicker.vfx.config;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig.Limits;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfig.Preset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

/**
 * Parses {@code effects.yml} into an {@link EffectsConfig}. Never throws on bad content: every problem is collected
 * (the caller logs them) and the bad layer or preset is skipped. Needs no running server, so it is unit-tested and
 * used by the preview export. Item, block and model names are only checked for syntax here; {@code Effects.load()}
 * resolves them against the server.
 */
public final class EffectsConfigParser {

    public static final String LIMITS = "limits";
    /** Preset-level field: ticks between plays of {@code Effects.loop}. */
    public static final String LOOP_EVERY = "loop-every";
    /** Preset-level field: least ticks between two plays for one viewer (a per-viewer rate cap). */
    public static final String MIN_INTERVAL = "min-interval-ticks";
    static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    static final double[] ZERO = {0, 0, 0};
    private static final String TYPES = "particle, floating-text, item-crumbs, scale-pop or block-drop";

    private EffectsConfigParser() {
    }

    /**
     * Parses the server's effects.yml. Presets and limit values missing from it come from {@code defaults} (the
     * jar's effects.yml, or null), so new effects in a plugin update work with an old file.
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
                f.integer("max-display-entities-global", def.maxDisplayEntitiesGlobal(), 0, 2000),
                f.integer("max-falling-blocks-per-player", def.maxFallingBlocksPerPlayer(), 0, 256),
                f.integer("max-falling-blocks-global", def.maxFallingBlocksGlobal(), 0, 4000),
                f.integer("max-loops-per-player", def.maxLoopsPerPlayer(), 0, 32));
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
                if (!key.equals("layers") && !key.equals(LOOP_EVERY) && !key.equals(MIN_INTERVAL)) {
                    problems.add(id + ": unknown field '" + key + "' ignored");
                }
            }
            int loopEvery = loopEvery(id, s.get(LOOP_EVERY), problems);
            int minInterval = minInterval(id, s.get(MIN_INTERVAL), problems);
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
            layers = ParticleLayerParser.enforceCap(id, layers, limits, problems);
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
            out.put(id, new Preset(id, List.copyOf(layers), loopEvery, minInterval));
        }
        return out;
    }

    /** {@code loop-every}: whole ticks 1..1200, or absent (0). Bad values are logged and the field ignored. */
    private static int loopEvery(String id, @Nullable Object raw, List<String> problems) {
        if (raw == null) {
            return 0;
        }
        if ((raw instanceof Integer || raw instanceof Long) && ((Number) raw).longValue() >= 1
                && ((Number) raw).longValue() <= 1200) {
            return ((Number) raw).intValue();
        }
        problems.add(id + ": 'loop-every' must be a whole number of ticks 1..1200, got '" + raw
                + "'; loops of this effect use " + DEFAULT_LOOP_EVERY);
        return 0;
    }

    /** {@code min-interval-ticks}: whole ticks 0..1200, or absent (0 = no cap). Bad values are logged, no cap. */
    private static int minInterval(String id, @Nullable Object raw, List<String> problems) {
        if (raw == null) {
            return 0;
        }
        if ((raw instanceof Integer || raw instanceof Long) && ((Number) raw).longValue() >= 0
                && ((Number) raw).longValue() <= 1200) {
            return ((Number) raw).intValue();
        }
        problems.add(id + ": 'min-interval-ticks' must be a whole number of ticks 0..1200, got '" + raw
                + "'; this effect plays uncapped");
        return 0;
    }

    /** Interval used by {@code Effects.loop} for presets without a valid {@code loop-every}. */
    public static final int DEFAULT_LOOP_EVERY = 20;

    static @Nullable LayerSpec layer(String id, int index, Map<?, ?> map, List<String> problems) {
        Object typeRaw = map.get("type");
        String type = typeRaw == null ? "" : Fields.lower(typeRaw.toString());
        String label = index + ":" + type;
        Fields f = new Fields(map, id + " layer " + index + " (" + (type.isEmpty() ? "?" : type) + ")", problems);
        f.raw("type");
        LayerSpec spec = switch (type) {
            case "particle" -> ParticleLayerParser.particle(label, f);
            case "floating-text" -> DisplayLayerParsers.floatingText(label, f);
            case "item-crumbs" -> DisplayLayerParsers.itemCrumbs(label, f);
            case "scale-pop" -> DisplayLayerParsers.scalePop(label, f);
            case "block-drop" -> DisplayLayerParsers.blockDrop(label, f);
            case "" -> {
                f.fail("missing 'type' (" + TYPES + ")");
                yield null;
            }
            default -> {
                f.fail("unknown type '" + type + "' (use " + TYPES + ")");
                yield null;
            }
        };
        if (spec == null || f.fatal()) {
            return null;
        }
        f.reportUnknown();
        return spec;
    }


    static <E extends Enum<E>> E enumValue(Fields f, String key, Class<E> type, E def) {
        String v = f.string(key);
        if (v == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            f.problem("'" + key + "' '" + v + "' is not one of "
                    + Arrays.toString(type.getEnumConstants()).toLowerCase(Locale.ROOT) + "; using "
                    + def.name().toLowerCase(Locale.ROOT));
            return def;
        }
    }

    /** Resolves {@code dust} or {@code minecraft:dust} against the {@link Particle} enum. */
    public static @Nullable Particle particleByKey(String name) {
        return ParticleLayerParser.particleByKey(name);
    }
}
