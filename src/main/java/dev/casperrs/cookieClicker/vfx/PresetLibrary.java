package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.text.MarkerTemplate;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * The loaded presets: reads {@code effects.yml}, and resolves each layer's server-side data (ItemStacks,
 * BlockData, particle data, floating-text templates) once, so playing an effect allocates as little as possible.
 */
final class PresetLibrary {

    static final String FILE = "effects.yml";
    /** The only floating-text placeholder that has a precompiled template; others fall back to a real parse. */
    static final String AMOUNT = "amount";

    /** A preset with its server-side data resolved once at load. */
    record Compiled(EffectsConfig.Preset preset, List<Resolved> layers) {
    }

    /**
     * One layer plus its data: {@code plain} for viewers without our pack, {@code modelled} with it. For a
     * floating-text layer both hold its {@link MarkerTemplate}.
     */
    record Resolved(LayerSpec spec, @Nullable Object plain, @Nullable Object modelled) {
    }

    private final Plugin plugin;
    private final MiniMessage miniMessage;
    private final WarnOnce warn;
    private Map<String, Compiled> presets = Map.of();

    PresetLibrary(Plugin plugin, MiniMessage miniMessage, WarnOnce warn) {
        this.plugin = plugin;
        this.miniMessage = miniMessage;
        this.warn = warn;
    }

    /** Reads and resolves the file (the jar's defaults fill in missing presets); logs problems, never throws. */
    EffectsConfig load() {
        EffectsConfig config = readConfig();
        for (String problem : config.problems()) {
            plugin.getLogger().warning("effects.yml: " + problem);
        }
        Map<String, Compiled> compiled = new HashMap<>();
        for (EffectsConfig.Preset preset : config.presets().values()) {
            List<Resolved> layers = new ArrayList<>();
            for (LayerSpec layer : preset.layers()) {
                Resolved r = resolve(preset.id(), layer);
                if (r != null) {
                    layers.add(r);
                }
            }
            compiled.put(preset.id(), new Compiled(preset, List.copyOf(layers)));
        }
        presets = Map.copyOf(compiled);
        return config;
    }

    PresetLibrary.@Nullable Compiled get(String id) {
        return presets.get(id);
    }

    boolean has(String id) {
        return presets.containsKey(id);
    }

    Set<String> ids() {
        return presets.keySet();
    }

    int size() {
        return presets.size();
    }

    /** The {@code block-drop} layer of {@code presetId}, or null. */
    LayerSpec.@Nullable BlockDrop blockDrop(String presetId) {
        Compiled compiled = presets.get(presetId);
        if (compiled != null) {
            for (Resolved r : compiled.layers()) {
                if (r.spec() instanceof LayerSpec.BlockDrop drop) {
                    return drop;
                }
            }
        }
        return null;
    }

    PopSequence popSequence(String presetId) {
        Compiled compiled = presets.get(presetId);
        if (compiled != null) {
            for (Resolved r : compiled.layers()) {
                if (r.spec() instanceof LayerSpec.ScalePop pop) {
                    return pop.sequence();
                }
            }
        }
        warn.warn("pop:" + presetId, "Effect '" + presetId + "' has no scale-pop layer; using the standard pop");
        return PopSequence.standard();
    }

    private EffectsConfig readConfig() {
        YamlConfiguration defaults = null;
        try (InputStream in = plugin.getResource(FILE)) {
            if (in != null) {
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    defaults = YamlConfiguration.loadConfiguration(reader);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the built-in " + FILE, e);
        }
        File file = new File(plugin.getDataFolder(), FILE);
        if (!file.exists() && defaults != null) {
            plugin.saveResource(FILE, false);
        }
        YamlConfiguration user = new YamlConfiguration();
        try {
            if (file.exists()) {
                user.load(file);
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe(FILE + " is not valid YAML, using the built-in effects: " + e.getMessage());
            user = new YamlConfiguration();
        }
        return EffectsConfigParser.parse(user, defaults);
    }

    /** Builds the Bukkit data objects for a layer; null (and logged) when the server rejects them. */
    private @Nullable Resolved resolve(String id, LayerSpec layer) {
        try {
            return switch (layer) {
                case LayerSpec.Particles p -> particleData(p);
                case LayerSpec.ItemCrumbs c -> {
                    ItemStack plain = item(c.item());
                    yield new Resolved(c, plain, modelled(plain, c.itemModel()));
                }
                case LayerSpec.FloatingText t -> {
                    MarkerTemplate template = MarkerTemplate.of(miniMessage, t.text(), AMOUNT);
                    yield new Resolved(t, template, template);
                }
                default -> new Resolved(layer, null, null);
            };
        } catch (RuntimeException e) {
            plugin.getLogger().warning("effects.yml: " + id + " layer " + layer.label() + ": " + e.getMessage()
                    + " - layer skipped");
            return null;
        }
    }

    private Resolved particleData(LayerSpec.Particles p) {
        LayerSpec.ParticleData d = p.data();
        Class<?> type = d.type();
        Color color = d.color() == null ? Color.WHITE : d.color();
        Object plain;
        Object modelled = null;
        if (type == Void.class) {
            plain = null;
        } else if (type == Particle.DustOptions.class) {
            plain = new Particle.DustOptions(color, d.size());
        } else if (type == Particle.DustTransition.class) {
            plain = new Particle.DustTransition(color, d.toColor() == null ? color : d.toColor(), d.size());
        } else if (type == Color.class) {
            plain = color;
        } else if (type == Particle.Spell.class) {
            plain = new Particle.Spell(color, d.power());
        } else if (type == ItemStack.class) {
            ItemStack item = item(d.item() == null ? "cookie" : d.item());
            plain = item;
            modelled = modelled(item, d.itemModel());
        } else if (type == BlockData.class) {
            Material m = Material.matchMaterial(d.block() == null ? "" : d.block());
            if (m == null || !m.isBlock()) {
                throw new IllegalArgumentException("'" + d.block() + "' is not a block");
            }
            plain = m.createBlockData();
        } else if (type == Float.class) {
            plain = d.value();
        } else if (type == Integer.class) {
            plain = (int) d.value();
        } else {
            throw new IllegalArgumentException("unsupported particle data " + type.getSimpleName());
        }
        return new Resolved(p, plain, modelled == null ? plain : modelled);
    }

    private static ItemStack item(String name) {
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            throw new IllegalArgumentException("'" + name + "' is not an item");
        }
        return ItemStack.of(m);
    }

    /** A copy of {@code plain} carrying our {@code item_model}, or {@code plain} itself when there is none. */
    private static ItemStack modelled(ItemStack plain, @Nullable String model) {
        NamespacedKey key = model == null ? null : NamespacedKey.fromString(model);
        if (key == null) {
            return plain;
        }
        ItemStack copy = plain.clone();
        copy.setData(DataComponentTypes.ITEM_MODEL, key);
        return copy;
    }
}
