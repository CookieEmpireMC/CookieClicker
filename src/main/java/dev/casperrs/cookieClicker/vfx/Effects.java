package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.text.MarkerTemplate;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfig;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.PopSequence;
import dev.casperrs.cookieClicker.vfx.shape.Shapes;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The effects engine: everything else plays effects by ID, how they look lives in {@code effects.yml}.
 *
 * <p>Main thread only. Construct once, call {@link #load()} (also the reload) and {@link #shutdown()} in
 * {@code onDisable}. Never throws mid-game: bad config and failures are logged once per problem until the next
 * reload and the effect or layer is skipped. Personal effects ({@code viewer != null}) are sent to that player only;
 * every spawned entity is non-persistent, tagged, tracked and removed on completion, chunk unload, viewer quit,
 * reload and shutdown. {@code design/effects.md} has the details of the house cinematic API
 * ({@link #dropBlock}, {@link #loop}, {@link #cancelFor}).
 */
public final class Effects {

    /** Preset used by {@link #pop(Display, Player)}. */
    public static final String POP = "pop";
    /** Preset whose {@code block-drop} layer animates {@link #dropBlock}. */
    public static final String BLOCK_DROP = "house_block_drop";
    private static final int JITTER_SLOTS = 12;

    private final Plugin plugin;
    private final MiniMessage miniMessage;
    private final Spawner spawner;
    private final DisplayRegistry registry = new DisplayRegistry();
    private final EffectTicker ticker;
    private final VfxListener listener;
    private final Map<UUID, PopJob> pops = new HashMap<>();
    private final WarnOnce warn;
    private final PresetLibrary library;
    private final BlockDrops drops;
    private final Loops loops;
    private final Throttle throttle = new Throttle();
    private EffectsConfig.Limits limits = EffectsConfig.Limits.DEFAULTS;
    private Predicate<Player> packCheck = p -> false;
    private boolean loaded;
    private boolean listening;
    private int jitter;

    public Effects(Plugin plugin) {
        this(plugin, MiniMessage.miniMessage());
    }

    /** @param miniMessage the plugin's shared MiniMessage instance (floating text is parsed with it) */
    public Effects(Plugin plugin, MiniMessage miniMessage) {
        this.plugin = plugin;
        this.miniMessage = miniMessage;
        this.spawner = new Spawner(plugin);
        this.ticker = new EffectTicker(plugin);
        this.warn = new WarnOnce(plugin.getLogger());
        this.library = new PresetLibrary(plugin, miniMessage, warn);
        this.drops = new BlockDrops(this, spawner, ticker, library, warn);
        this.loops = new Loops(this, ticker, library, warn);
        this.listener = new VfxListener(registry, spawner, this::tracks, this::viewerGone);
    }


    /** Loads (or reloads) {@code effects.yml}, stopping every running effect and sweeping leftover entities. Problems are logged, never thrown. */
    public void load() {
        ticker.cancelAll();
        pops.clear();
        throttle.clear();
        warn.clear();
        EffectsConfig config = library.load();
        limits = config.limits();
        registry.limits(limits);
        if (!listening) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            listening = true;
        }
        loaded = true;
        sweepLeftovers();
        plugin.getLogger().info("Loaded " + library.size() + " effect(s) from " + PresetLibrary.FILE);
    }

    public void reload() {
        load();
    }

    /** Stops every effect and removes its entities (pops put the display back at rest scale). Safe to call twice. */
    public void shutdown() {
        ticker.cancelAll();
        pops.clear();
        throttle.clear();
        if (listening) {
            HandlerList.unregisterAll(listener);
            listening = false;
        }
        if (loaded) {
            sweepLeftovers();
        }
        loaded = false;
    }

    /** Removes entities in loaded worlds that carry our PDC key and aren't a running effect; {@link #load()} calls it. */
    public int sweepLeftovers() {
        if (!onMainThread("sweepLeftovers", null)) {
            return 0;
        }
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (spawner.isOurs(entity) && !tracks(entity.getUniqueId())) {
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

    /** Which viewers have our resource pack: only they get layers with an {@code item-model}, others the plain vanilla item. */
    public void setPackCheck(Predicate<Player> hasPack) {
        this.packCheck = hasPack;
    }


    public void play(String effectId, Location loc, @Nullable Player viewer) {
        play(effectId, loc, viewer, Map.of());
    }

    /** Plays an effect at {@code loc} for {@code viewer} (null = everyone nearby). Placeholder values are inserted as unparsed text, so player-influenced values are safe. A preset with {@code min-interval-ticks} silently drops plays that come sooner. */
    public void play(String effectId, Location loc, @Nullable Player viewer, Map<String, String> placeholders) {
        if (!ready("play", effectId)) {
            return;
        }
        PresetLibrary.Compiled compiled = library.get(effectId);
        if (compiled == null) {
            warn.warn("unknown:" + effectId, "Unknown effect ID '" + effectId + "' (not in effects.yml); skipped");
            return;
        }
        int interval = compiled.preset().minIntervalTicks();
        if (interval > 0 && !throttle.allow(viewer == null ? null : viewer.getUniqueId(), effectId,
                plugin.getServer().getCurrentTick(), interval)) {
            return;
        }
        playLayers(compiled, loc, viewer, placeholders);
    }

    void playLayers(PresetLibrary.Compiled compiled, Location loc, @Nullable Player viewer,
                            Map<String, String> placeholders) {
        String effectId = compiled.preset().id();
        try {
            World world = loc.getWorld();
            if (world == null || (viewer != null && (!viewer.isOnline() || !viewer.getWorld().equals(world)))) {
                return;
            }
            boolean pack = viewer != null && packCheck.test(viewer);
            for (PresetLibrary.Resolved layer : compiled.layers()) {
                Object data = pack ? layer.modelled() : layer.plain();
                switch (layer.spec()) {
                    case LayerSpec.Particles p -> ticker.start(new ParticleJob(p, data, loc, viewer));
                    case LayerSpec.FloatingText t -> {
                        Component text = floatingText(t, data, placeholders);
                        double[] j = Shapes.goldenDisc(Math.floorMod(jitter++, JITTER_SLOTS), JITTER_SLOTS, t.spread());
                        ticker.start(new FloatingTextJob(spawner, registry, t, text, loc, viewer, j[0], j[1]));
                    }
                    case LayerSpec.ItemCrumbs c when data instanceof ItemStack item ->
                            ticker.start(new CrumbJob(spawner, registry, c, item, loc, viewer));
                    default -> {
                        // scale-pop / block-drop layers only run through pop() / dropBlock(); crumbs without an
                        // item were logged at load
                    }
                }
            }
        } catch (RuntimeException e) {
            warn.warn("error:" + effectId, "Effect '" + effectId + "' failed and was skipped", e);
        }
    }

    public void pop(Display display, @Nullable Player viewer) {
        pop(display, viewer, POP);
    }

    /** Squash-and-pops an existing display with the scale-pop layer of {@code presetId}, keeping the caller's translation and rotation. A pop during a pop restarts from the same rest scale; the display stays the caller's. */
    public void pop(Display display, @Nullable Player viewer, String presetId) {
        pop(display, viewer, presetId, null);
    }

    /** Like {@link #pop(Display, Player, String)}, but each step also sets translation and rotation from {@code motion}, so the caller's spin and bob keep running inside the same interpolation. */
    public void pop(Display display, @Nullable Player viewer, String presetId, @Nullable PopMotion motion) {
        if (!ready("pop", presetId)) {
            return;
        }
        try {
            if (!display.isValid() || (viewer != null && !viewer.isOnline())) {
                return;
            }
            PopSequence sequence = library.popSequence(presetId);
            PopJob running = pops.get(display.getUniqueId());
            Vector3f base = running != null ? running.base() : new Vector3f(display.getTransformation().getScale());
            if (running != null) {
                running.handOver();
            }
            PopJob job = new PopJob(display, base, sequence, motion, ended -> pops.remove(ended.display().getUniqueId(), ended));
            pops.put(display.getUniqueId(), job);
            ticker.start(job);
        } catch (RuntimeException e) {
            warn.warn("error:pop:" + presetId, "Pop '" + presetId + "' failed and was skipped", e);
        }
    }


    public boolean dropBlock(Player viewer, BlockData data, Location target, Runnable onLanded) {
        return dropBlock(viewer, data, target, null, onLanded);
    }

    /**
     * Drops a block for one viewer: a {@code BlockDisplay} of {@code data} (only {@code viewer} sees it) spawns above
     * {@code target}'s block, falls, bounces and settles as set by the {@code block-drop} layer of
     * {@value #BLOCK_DROP}. At the impact the engine plays the layer's {@code impact-effect} and a few block
     * particles; at the landing the display is removed and {@code onLanded} runs in the same tick, so the caller
     * can resend the real block without a gap.
     *
     * <p>Callback rules: {@code onImpact} and {@code onLanded} each run at most once, {@code onImpact} first, both on
     * the main thread; exceptions they throw are logged and swallowed.
     * <ul>
     *   <li><b>true</b>: the block is falling. {@code onLanded} runs when it settles (12 ticks with the default
     *       preset), or earlier if the display disappears first (chunk unload) while the viewer is still there. It
     *       never runs after {@link #cancelFor}, viewer quit or world change, {@link #reload()} or
     *       {@link #shutdown()}; those remove the display without any further callback.</li>
     *   <li><b>false, callbacks already ran</b> ("landed instantly", inside this call): the viewer is at
     *       {@code limits.max-falling-blocks-per-player} or the server at {@code max-falling-blocks-global}, the
     *       block data is air, the engine isn't loaded, or the spawn failed. Impact effects still play.</li>
     *   <li><b>false, nothing ran</b>: called off the main thread, or the viewer is offline or in another world
     *       than {@code target}. The caller restores the block itself.</li>
     * </ul>
     *
     * @param onImpact run when the block first touches the ground (before the bounce), or null
     * @return true when the block is animating, false when it landed instantly or was skipped (see above)
     */
    public boolean dropBlock(Player viewer, BlockData data, Location target, @Nullable Runnable onImpact,
                             Runnable onLanded) {
        return drops.drop(viewer, data, target, onImpact, onLanded);
    }

    public EffectHandle loop(String effectId, Location loc, Player viewer) {
        return loop(effectId, loc, viewer, null);
    }

    /**
     * Replays a preset for one viewer (e.g. {@code house_chimney_smoke} at the chimney top) every
     * {@code loop-every} ticks of that preset ({@value EffectsConfigParser#DEFAULT_LOOP_EVERY} if it has none), the
     * first play right now. Plays are skipped while the location's chunk isn't loaded. The loop ends on
     * {@link EffectHandle#cancel()}, {@link #cancelFor}, viewer quit or world change, {@link #reload()} and
     * {@link #shutdown()}; start it again after a reload.
     *
     * @param onRepeat run right after every play (e.g. to replay a crackle sound every n-th cycle), or null
     * @return the running loop, or {@link EffectHandle#NONE} (already inactive) when the ID is unknown, the viewer
     *         is offline or in another world, {@code limits.max-loops-per-player} is reached (logged once), or the
     *         engine isn't loaded / not on the main thread
     */
    public EffectHandle loop(String effectId, Location loc, Player viewer, @Nullable Runnable onRepeat) {
        return loops.start(effectId, loc, viewer, 0, onRepeat);
    }

    /**
     * Like {@link #loop(String, Location, Player, Runnable)}, but the first play comes after {@code startDelay}
     * ticks (e.g. to start several vents out of phase) instead of right now.
     */
    public EffectHandle loop(String effectId, Location loc, Player viewer, int startDelay,
                             @Nullable Runnable onRepeat) {
        return loops.start(effectId, loc, viewer, Math.max(0, startDelay), onRepeat);
    }

    /** Stops this viewer's falling blocks (no callbacks) and loops; click numbers and crumbs are left alone. */
    public void cancelFor(Player viewer) {
        if (!onMainThread("cancelFor", null)) {
            return;
        }
        UUID id = viewer.getUniqueId();
        drops.cancelViewer(id);
        loops.cancelViewer(id);
    }

    public int activeFallingBlocks() {
        return drops.total();
    }

    public int activeLoops() {
        return loops.total();
    }

    public boolean has(String effectId) {
        return library.has(effectId);
    }

    public Set<String> effectIds() {
        return library.ids();
    }

    public NamespacedKey entityKey() {
        return spawner.key();
    }

    public int activeDisplayEntities() {
        return registry.globalEntities();
    }


    private boolean tracks(UUID entity) {
        return registry.tracks(entity) || drops.tracks(entity);
    }

    private void viewerGone(UUID viewer) {
        throttle.forget(viewer);
        registry.cancelViewer(viewer);
        drops.cancelViewer(viewer);
        loops.cancelViewer(viewer);
    }

    void safeRun(Runnable callback, String what) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Effects: the " + what + " callback threw", e);
        }
    }

    boolean ready(String verb, String id) {
        if (!onMainThread(verb, id)) {
            return false;
        }
        if (!loaded) {
            warn.warn("not-loaded", "Effects." + what(verb, id) + " was called before Effects.load(); skipped");
            return false;
        }
        return true;
    }

    boolean onMainThread(String verb, @Nullable String id) {
        if (Bukkit.isPrimaryThread()) {
            return true;
        }
        warn.warn("async", "Effects." + what(verb, id)
                + " was called off the main thread; skipped (effects are main-thread only)");
        return false;
    }

    boolean isLoaded() {
        return loaded;
    }

    EffectsConfig.Limits limits() {
        return limits;
    }

    private static String what(String verb, @Nullable String id) {
        return id == null ? verb + "()" : verb + "('" + id + "')";
    }

    /** The precompiled template when only {@code amount} is given, else a real parse. */
    private Component floatingText(LayerSpec.FloatingText layer, @Nullable Object data,
                                   Map<String, String> placeholders) {
        String amount = placeholders.size() == 1 ? placeholders.get(PresetLibrary.AMOUNT) : null;
        if (amount != null && data instanceof MarkerTemplate template) {
            return template.render(amount);
        }
        return miniMessage.deserialize(layer.text(), resolver(placeholders));
    }

    private static TagResolver resolver(Map<String, String> placeholders) {
        List<TagResolver> list = new ArrayList<>(placeholders.size());
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            list.add(Placeholder.unparsed(e.getKey(), e.getValue()));
        }
        return TagResolver.resolver(list);
    }
}
