package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import dev.casperrs.cookieClicker.vfx.shape.Shapes;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The effects engine. Everything else plays effects by ID; how they look lives in {@code effects.yml}.
 *
 * <p>Lifecycle (main thread only): construct once in {@code onEnable}, call {@link #load()} (also the reload),
 * call {@link #shutdown()} in {@code onDisable}. {@code load()} also sweeps leftover effect entities.
 *
 * <p>Never throws mid-game: unknown IDs, bad config and failures are logged (once per problem until the next
 * reload) and the effect or layer is skipped. Personal effects ({@code viewer != null}) are only sent to that
 * player: particles via {@link Player#spawnParticle}, display entities hidden by default and shown to the viewer.
 * Every spawned entity is non-persistent, tagged ({@code cookieclicker} scoreboard tag + {@code cookieclicker:vfx}
 * PDC key), tracked and removed on completion, chunk unload, viewer quit, reload and shutdown.
 */
public final class Effects {

    /** Preset used by {@link #pop(Display, Player)}. */
    public static final String POP = "pop";
    private static final String FILE = "effects.yml";
    private static final int JITTER_SLOTS = 12;

    private final Plugin plugin;
    private final MiniMessage miniMessage;
    private final Spawner spawner;
    private final DisplayRegistry registry = new DisplayRegistry();
    private final EffectTicker ticker;
    private final VfxListener listener;
    private final Map<UUID, PopJob> pops = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Map<String, Compiled> presets = Map.of();
    private Predicate<Player> packCheck = p -> false;
    private boolean loaded;
    private boolean listening;
    private int jitter;

    /** A preset with its server-side data (ItemStacks, BlockData, particle data) resolved once at load. */
    private record Compiled(EffectsConfig.Preset preset, List<Resolved> layers) {
    }

    /** One layer plus its data: {@code plain} for viewers without our pack, {@code modelled} with it. */
    private record Resolved(LayerSpec spec, @Nullable Object plain, @Nullable Object modelled) {
    }

    public Effects(Plugin plugin) {
        this(plugin, MiniMessage.miniMessage());
    }

    /** @param miniMessage the plugin's shared MiniMessage instance (floating text is parsed with it) */
    public Effects(Plugin plugin, MiniMessage miniMessage) {
        this.plugin = plugin;
        this.miniMessage = miniMessage;
        this.spawner = new Spawner(plugin);
        this.ticker = new EffectTicker(plugin);
        this.listener = new VfxListener(registry, spawner);
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------

    /**
     * Loads (or reloads) {@code effects.yml} from the data folder, writing the default file first if it's missing.
     * Stops every running effect, removes its entities, and sweeps leftover effect entities in loaded worlds.
     * Presets missing from the server's file are taken from the jar's defaults. Problems are logged, never thrown.
     */
    public void load() {
        ticker.cancelAll();
        pops.clear();
        warned.clear();
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
        registry.limits(config.limits());
        if (!listening) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            listening = true;
        }
        loaded = true;
        sweepLeftovers();
        plugin.getLogger().info("Loaded " + presets.size() + " effect(s) from " + FILE);
    }

    /** Same as {@link #load()}. */
    public void reload() {
        load();
    }

    /**
     * Stops every effect, removes every effect entity (pops put the display back at its rest scale), unregisters
     * the listener and sweeps leftovers. Call in {@code onDisable}. Safe to call twice.
     */
    public void shutdown() {
        ticker.cancelAll();
        pops.clear();
        if (listening) {
            HandlerList.unregisterAll(listener);
            listening = false;
        }
        if (loaded) {
            sweepLeftovers();
        }
        loaded = false;
    }

    /**
     * Removes every entity in loaded worlds that carries our {@code cookieclicker:vfx} PDC key and isn't a running
     * effect (leftovers from a crash). {@link #load()} already calls this; chunks loaded later are swept by the
     * engine's listener.
     *
     * @return how many entities were removed
     */
    public int sweepLeftovers() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (spawner.isOurs(entity) && !registry.tracks(entity.getUniqueId())) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Removed " + removed + " leftover effect entit" + (removed == 1 ? "y" : "ies"));
        }
        return removed;
    }

    /**
     * Tells the engine which viewers have our resource pack loaded. Layers with an {@code item-model} use it only
     * for those viewers (others get the plain vanilla item, never the missing-model texture). Default: nobody.
     */
    public void setPackCheck(Predicate<Player> hasPack) {
        this.packCheck = hasPack;
    }

    // ---- playing ----------------------------------------------------------------------------------------------

    /** Plays an effect with no placeholders. See {@link #play(String, Location, Player, Map)}. */
    public void play(String effectId, Location loc, @Nullable Player viewer) {
        play(effectId, loc, viewer, Map.of());
    }

    /**
     * Plays an effect at a location.
     *
     * @param effectId     an ID from {@code effects.yml} (e.g. {@code cookie_click}); unknown IDs are logged once
     * @param loc          where it plays; must be in a loaded world
     * @param viewer       the only player who sees it, or {@code null} to show it to everyone nearby
     * @param placeholders values for floating-text tags, e.g. {@code Map.of("amount", "15")}; always inserted as
     *                     unparsed text, so player-influenced values are safe
     */
    public void play(String effectId, Location loc, @Nullable Player viewer, Map<String, String> placeholders) {
        if (!ready("play('" + effectId + "')")) {
            return;
        }
        try {
            Compiled compiled = presets.get(effectId);
            if (compiled == null) {
                warnOnce("unknown:" + effectId, "Unknown effect ID '" + effectId + "' (not in effects.yml); skipped");
                return;
            }
            World world = loc.getWorld();
            if (world == null || (viewer != null && (!viewer.isOnline() || !viewer.getWorld().equals(world)))) {
                return;
            }
            boolean pack = viewer != null && packCheck.test(viewer);
            TagResolver resolver = null;
            for (Resolved layer : compiled.layers()) {
                Object data = pack ? layer.modelled() : layer.plain();
                switch (layer.spec()) {
                    case LayerSpec.Particles p -> ticker.start(new ParticleJob(p, data, loc, viewer));
                    case LayerSpec.FloatingText t -> {
                        if (resolver == null) {
                            resolver = resolver(placeholders);
                        }
                        Component text = miniMessage.deserialize(t.text(), resolver);
                        double[] j = Shapes.goldenDisc(jitter++ % JITTER_SLOTS, JITTER_SLOTS, t.spread());
                        ticker.start(new FloatingTextJob(spawner, registry, t, text, loc, viewer, j[0], j[1]));
                    }
                    case LayerSpec.ItemCrumbs c when data instanceof ItemStack item ->
                            ticker.start(new CrumbJob(spawner, registry, c, item, loc, viewer));
                    default -> {
                        // scale-pop layers only run through pop(); crumbs without an item were logged at load
                    }
                }
            }
        } catch (RuntimeException e) {
            warnOnce("error:" + effectId, "Effect '" + effectId + "' failed and was skipped", e);
        }
    }

    /** Squash-and-pops a display with the {@code pop} preset. See {@link #pop(Display, Player, String)}. */
    public void pop(Display display, @Nullable Player viewer) {
        pop(display, viewer, POP);
    }

    /**
     * Squash-and-pops an existing display (e.g. the clicked cookie): scale-only read-modify-write steps (by default
     * 2 + 2 + 3 ticks, interpolation delay 0), so the caller's translation and rotation (spin/bob) are kept. A pop
     * during a pop restarts from the same rest scale. The display stays the caller's; this never removes it.
     *
     * @param viewer   the player the display belongs to; if they went offline the pop is skipped
     * @param presetId an effect ID with a {@code scale-pop} layer
     */
    public void pop(Display display, @Nullable Player viewer, String presetId) {
        if (!ready("pop('" + presetId + "')")) {
            return;
        }
        try {
            if (!display.isValid() || (viewer != null && !viewer.isOnline())) {
                return;
            }
            PopSequence sequence = popSequence(presetId);
            PopJob running = pops.get(display.getUniqueId());
            Vector3f base = running != null ? running.base() : new Vector3f(display.getTransformation().getScale());
            if (running != null) {
                running.handOver();
            }
            PopJob job = new PopJob(display, base, sequence, ended -> pops.remove(ended.display().getUniqueId(), ended));
            pops.put(display.getUniqueId(), job);
            ticker.start(job);
        } catch (RuntimeException e) {
            warnOnce("error:pop:" + presetId, "Pop '" + presetId + "' failed and was skipped", e);
        }
    }

    /** True if {@code effectId} is loaded (useful for the coder's own checks and tests). */
    public boolean has(String effectId) {
        return presets.containsKey(effectId);
    }

    /** Every loaded effect ID. */
    public Set<String> effectIds() {
        return presets.keySet();
    }

    /** The PDC key on every effect entity ({@code cookieclicker:vfx}). */
    public NamespacedKey entityKey() {
        return spawner.key();
    }

    /** Effect display entities alive right now (for debug commands and the tester). */
    public int activeDisplayEntities() {
        return registry.globalEntities();
    }

    // ---- internals --------------------------------------------------------------------------------------------

    private boolean ready(String what) {
        if (!Bukkit.isPrimaryThread()) {
            warnOnce("async", "Effects." + what + " was called off the main thread; skipped (effects are main-thread only)");
            return false;
        }
        if (!loaded) {
            warnOnce("not-loaded", "Effects." + what + " was called before Effects.load(); skipped");
            return false;
        }
        return true;
    }

    private static TagResolver resolver(Map<String, String> placeholders) {
        List<TagResolver> list = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            list.add(Placeholder.unparsed(e.getKey(), e.getValue()));
        }
        return TagResolver.resolver(list);
    }

    private PopSequence popSequence(String presetId) {
        Compiled compiled = presets.get(presetId);
        if (compiled != null) {
            for (Resolved r : compiled.layers()) {
                if (r.spec() instanceof LayerSpec.ScalePop pop) {
                    return pop.sequence();
                }
            }
        }
        warnOnce("pop:" + presetId, "Effect '" + presetId + "' has no scale-pop layer; using the standard pop");
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

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            plugin.getLogger().warning(message);
        }
    }

    private void warnOnce(String key, String message, Throwable error) {
        if (warned.add(key)) {
            plugin.getLogger().log(Level.WARNING, message, error);
        }
    }
}
