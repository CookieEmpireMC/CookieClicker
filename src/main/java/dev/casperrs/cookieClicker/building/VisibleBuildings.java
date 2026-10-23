package dev.casperrs.cookieClicker.building;

import dev.casperrs.cookieClicker.config.BuildingVisualSettings;
import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.house.design.BuildingSlots;
import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.house.design.HouseDesignParser;
import dev.casperrs.cookieClicker.house.setup.HousePlacer;
import dev.casperrs.cookieClicker.house.setup.PlotSetup;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotPresenceListener;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotTracker;
import dev.casperrs.cookieClicker.session.SessionListener;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.vfx.Effects;
import dev.casperrs.cookieClicker.view.StationViews;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The bought bakery buildings on the factory plot (brief building-roster "Visible buildings"). Plots are one shared
 * location, so every player in a plot sees only <b>their own</b> buildings there: instance <i>i</i> of a building
 * stands on slot <i>i</i> of {@code houses/building-slots.yml}, up to its slot count, as an item display
 * {@code cookieclicker:building_world/<id>} ({@code custom_model_data} strings[0] = stage 1/2/3) or, without our pack,
 * a vanilla block display ({@code plot.buildings.fallback-blocks}).
 *
 * <p>Updated only on a change: a building purchase ({@link #bought}), session load, plot enter/leave, pack status,
 * {@code /bakery plot setup} and reload. No task, no per-tick work. Only a purchase is loud: new instances play
 * {@code building_appear}, a stage change plays {@code building_milestone} on every shown instance of that type, the
 * effects {@code plot.buildings.effect-stagger-ticks} apart, and at most one sound per purchase. Entities are
 * non-persistent, hidden by default and tagged {@value StationViews#TAG} (the enable sweep removes crash leftovers).
 * Main thread only.
 *
 * <p>The slot cells are layout cells of the {@value PlotSetup#DEFAULT_DESIGN} design; a plot's layout frame is worked
 * back from the house box its setup stored ({@link BuildingLayout#origin}). Plots without a setup show nothing.
 */
public final class VisibleBuildings implements Listener, PlotPresenceListener, SessionListener {

    public static final String EFFECT_APPEAR = "building_appear";
    public static final String EFFECT_MILESTONE = "building_milestone";
    public static final String SOUND_APPEAR = "building_appear";
    public static final String SOUND_MILESTONE = "building_milestone";

    private final Plugin plugin;
    private final PlotRegistry plots;
    private final PlotTracker tracker;
    private final SessionManager sessions;
    private final Effects effects;
    private final SoundService sounds;
    private final Predicate<Player> hasPack;
    private final Supplier<BuildingVisualSettings> settings;

    private BuildingSlots slots = BuildingSlots.empty();
    private @Nullable HouseDesign design;
    private final Map<UUID, View> views = new HashMap<>();
    /** Entity id → owner, for chunk unloads. */
    private final Map<UUID, UUID> owners = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private boolean running;

    /** One player's buildings in the plot they are in. */
    private static final class View {
        final String plotId;
        final World world;
        final int[] origin;
        final boolean withPack;
        final Map<String, List<Display>> shown = new HashMap<>();
        /** Owned count last shown per building. */
        final Map<String, Integer> owned = new HashMap<>();

        View(String plotId, World world, int[] origin, boolean withPack) {
            this.plotId = plotId;
            this.world = world;
            this.origin = origin;
            this.withPack = withPack;
        }

        List<Display> all() {
            List<Display> out = new ArrayList<>();
            shown.values().forEach(out::addAll);
            return out;
        }
    }

    /** One effect to play (staggered). */
    private record Play(String effect, Location at) {
    }

    public VisibleBuildings(Plugin plugin, PlotRegistry plots, PlotTracker tracker, SessionManager sessions,
                            Effects effects, SoundService sounds, Predicate<Player> hasPack,
                            Supplier<BuildingVisualSettings> settings) {
        this.plugin = plugin;
        this.plots = plots;
        this.tracker = tracker;
        this.sessions = sessions;
        this.effects = effects;
        this.sounds = sounds;
        this.hasPack = hasPack;
        this.settings = settings;
    }

    // ---- lifecycle ------------------------------------------------------------------------------------------

    /** Enable: reads the slots, hooks in and shows everyone already in a plot (quietly). */
    public void start() {
        if (!running) {
            running = true;
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            tracker.addListener(this);
            sessions.addListener(this);
        }
        load();
        showAll();
    }

    /** {@code /bakery reload}, after the plots were re-read and presence re-synced: rebuilt quietly. */
    public void reload() {
        removeAll();
        warned.clear();
        load();
        showAll();
    }

    /** {@code /bakery plot setup} (or undo) changed a plot: its viewers are rebuilt quietly. */
    public void plotChanged(String plotId) {
        for (UUID id : List.copyOf(views.keySet())) {
            if (views.get(id).plotId.equals(plotId)) {
                remove(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    show(p, false);
                }
            }
        }
    }

    public void shutdown() {
        removeAll();
        if (running) {
            HandlerList.unregisterAll(this);
            tracker.removeListener(this);
            sessions.removeListener(this);
            running = false;
        }
    }

    /** A purchase of buildings went through: the new ones appear (loud). Nothing else is ever loud. */
    public void bought(Player player) {
        show(player, views.containsKey(player.getUniqueId()));
    }

    // ---- files ----------------------------------------------------------------------------------------------

    /** Reads houses/building-slots.yml and the factory design (data folder first, then the jar). Small files. */
    private void load() {
        slots = BuildingSlots.empty();
        design = null;
        Path data = plugin.getDataFolder().toPath();
        try {
            String slotsText = read(data, PlotSetup.BUILDING_SLOTS);
            if (slotsText == null) {
                return; // no slots shipped or installed: no visible buildings
            }
            BuildingSlots.Result r = BuildingSlots.parse(yaml(slotsText));
            r.problems().forEach(p -> plugin.getLogger().warning("houses/" + PlotSetup.BUILDING_SLOTS + ": " + p));
            String designText = read(data, PlotSetup.DEFAULT_DESIGN + ".yml");
            if (designText == null) {
                plugin.getLogger().warning("houses/" + PlotSetup.DEFAULT_DESIGN + ".yml not found; no visible buildings");
                return;
            }
            HouseDesignParser.Result d = HouseDesignParser.parse(PlotSetup.DEFAULT_DESIGN, yaml(designText));
            if (d.design() == null || d.design().layout() == null) {
                plugin.getLogger().warning("houses/" + PlotSetup.DEFAULT_DESIGN
                        + ".yml has no valid layout; no visible buildings");
                return;
            }
            slots = r.slots();
            design = d.design();
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read houses/" + PlotSetup.BUILDING_SLOTS
                    + "; no visible buildings", e);
        }
    }

    private @Nullable String read(Path dataFolder, String file) throws IOException {
        Path own = dataFolder.resolve(HousePlacer.DESIGN_DIR).resolve(file);
        if (Files.isRegularFile(own)) {
            return Files.readString(own, StandardCharsets.UTF_8);
        }
        try (InputStream in = plugin.getResource(HousePlacer.DESIGN_DIR + "/" + file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, Object> yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return YamlFiles.toMap(y);
    }

    // ---- showing --------------------------------------------------------------------------------------------

    private void showAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            show(p, false);
        }
    }

    /**
     * Brings {@code player}'s buildings in line with their pouch and the plot they are in.
     *
     * @param loud play the appear/milestone effects and one sound (a purchase only)
     */
    private void show(Player player, boolean loud) {
        UUID id = player.getUniqueId();
        BuildingVisualSettings s = settings.get();
        CookiePouch pouch = sessions.get(id).orElse(null);
        String plotId = tracker.presence(id).plot();
        Plot plot = plotId == null ? null : plots.get(plotId).orElse(null);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        int[] origin = plot == null || design == null ? null : BuildingLayout.origin(plot, design);
        if (!s.enabled() || pouch == null || world == null || origin == null || slots.slots().isEmpty()) {
            remove(id);
            return;
        }
        boolean pack = hasPack.test(player);
        View view = views.get(id);
        if (view != null && (!view.plotId.equals(plotId) || view.withPack != pack || !view.world.equals(world))) {
            remove(id);
            view = null;
        }
        if (view == null) {
            view = new View(plotId, world, origin, pack);
            views.put(id, view);
            loud = false; // a fresh view (join, enter, reload, pack change) never plays anything
        }
        List<Play> plays = new ArrayList<>();
        String sound = null;
        Location soundAt = null;
        try {
            for (Map.Entry<String, List<BuildingSlots.Slot>> e : slots.slots().entrySet()) {
                String building = e.getKey();
                List<BuildingSlots.Slot> buildingSlots = e.getValue();
                int owned = pouch.owned(building);
                Integer before = view.owned.put(building, owned);
                BuildingLayout.Change c = BuildingLayout.change(before == null ? -1 : before, owned,
                        buildingSlots.size(), s);
                List<Display> shown = view.shown.computeIfAbsent(building, k -> new ArrayList<>());
                while (shown.size() > c.shownAfter()) { // only after a balance/slot reload: no selling
                    Display d = shown.removeLast();
                    owners.remove(d.getUniqueId());
                    d.remove();
                }
                if (c.stageChanged() && view.withPack) {
                    ItemStack stack = modelStack(building, c.stageAfter(), s);
                    for (Display d : shown) {
                        if (d instanceof ItemDisplay item) {
                            item.setItemStack(stack);
                        }
                    }
                }
                int first = shown.size();
                for (int i = first; i < c.shownAfter(); i++) {
                    Display d = spawn(player, view, building, buildingSlots.get(i), c.stageAfter(), s);
                    if (d == null) {
                        break; // chunk not loaded: the rest shows on the next change
                    }
                    shown.add(d);
                    if (loud) {
                        plays.add(new Play(EFFECT_APPEAR, d.getLocation()));
                        if (sound == null) {
                            sound = SOUND_APPEAR;
                            soundAt = d.getLocation();
                        }
                    }
                }
                if (loud && c.stageChanged() && !shown.isEmpty()) {
                    for (Display d : shown) {
                        plays.add(new Play(EFFECT_MILESTONE, d.getLocation()));
                    }
                    sound = SOUND_MILESTONE; // the bigger moment wins the one sound
                    soundAt = shown.getFirst().getLocation();
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not show the buildings of " + player.getName(), ex);
            remove(id);
            return;
        }
        if (sound != null) {
            sounds.play(player, sound, soundAt);
        }
        // one purchase may cross a stage for many types at once: cap its effects (the sound above is one anyway)
        play(player, plays.subList(0, Math.min(plays.size(), s.maxEffectsPerPurchase())), s.effectStaggerTicks());
    }

    /** Plays {@code plays} in order, {@code stagger} ticks apart (the first right away). */
    private void play(Player player, List<Play> plays, int stagger) {
        for (int i = 0; i < plays.size(); i++) {
            Play p = plays.get(i);
            long delay = (long) i * stagger;
            if (delay == 0) {
                effects.play(p.effect(), p.at(), player);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        effects.play(p.effect(), p.at(), player);
                    }
                }, delay);
            }
        }
    }

    private @Nullable Display spawn(Player owner, View view, String building, BuildingSlots.Slot slot, int stage,
                                    BuildingVisualSettings s) {
        double[] at = BuildingLayout.floorPoint(view.origin, slot);
        if (!view.world.isChunkLoaded((int) Math.floor(at[0]) >> 4, (int) Math.floor(at[2]) >> 4)) {
            return null;
        }
        Location loc = new Location(view.world, at[0], at[1], at[2], slot.yaw() + s.yawOffset(), 0);
        Display d;
        if (view.withPack) {
            ItemStack stack = modelStack(building, stage, s);
            d = view.world.spawn(loc, ItemDisplay.class, e -> {
                prepare(e, s);
                e.setItemStack(stack);
                // model bottom at model y 8 = the display position (design/buildings-world.md), scale 1
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            });
        } else {
            BlockData block = fallbackBlock(building, s);
            float sc = s.fallbackScale();
            d = view.world.spawn(loc, BlockDisplay.class, e -> {
                prepare(e, s);
                e.setBlock(block);
                // a block display grows from its corner: centre it on the floor point
                e.setTransformation(new Transformation(new Vector3f(-sc / 2, 0, -sc / 2), new Quaternionf(),
                        new Vector3f(sc, sc, sc), new Quaternionf()));
            });
        }
        owners.put(d.getUniqueId(), owner.getUniqueId());
        owner.showEntity(plugin, d);
        return d;
    }

    /** Hidden from everyone at spawn (shown to the owner only), non-persistent, tagged for the sweep. */
    private static void prepare(Display d, BuildingVisualSettings s) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.addScoreboardTag(StationViews.TAG);
        d.setViewRange(s.viewRange());
    }

    /** {@code cookieclicker:building_world/<id>} with {@code custom_model_data} strings[0] = the stage. */
    private ItemStack modelStack(String building, int stage, BuildingVisualSettings s) {
        Material base = fallbackBlock(building, s).getMaterial();
        ItemStack stack = ItemStack.of(base.isItem() && !base.isAir() ? base : Material.PAPER);
        stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", "building_world/" + building));
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addString(Integer.toString(stage)).build());
        return stack;
    }

    private BlockData fallbackBlock(String building, BuildingVisualSettings s) {
        String name = s.fallbackFor(building);
        try {
            BlockData data = Bukkit.createBlockData(name);
            if (!data.getMaterial().isAir()) {
                return data;
            }
        } catch (IllegalArgumentException ignored) {
            // warned below
        }
        if (warned.add(building)) {
            plugin.getLogger().warning("config.yml: plot.buildings fallback block '" + name + "' for " + building
                    + " is not a block; using barrel");
        }
        return Material.BARREL.createBlockData();
    }

    private void remove(UUID player) {
        View v = views.remove(player);
        if (v != null) {
            for (Display d : v.all()) {
                owners.remove(d.getUniqueId());
                d.remove();
            }
        }
    }

    private void removeAll() {
        for (UUID id : List.copyOf(views.keySet())) {
            remove(id);
        }
        owners.clear();
    }

    // ---- hooks ----------------------------------------------------------------------------------------------

    @Override
    public void onEnterPlot(Player player, String plotId) {
        show(player, false);
    }

    @Override
    public void onLeavePlot(Player player, String plotId) {
        remove(player.getUniqueId());
    }

    @Override
    public void onLoaded(UUID player, CookiePouch pouch) {
        Player p = Bukkit.getPlayer(player);
        if (p != null) {
            show(p, false);
        }
    }

    @Override
    public void onUnloaded(UUID player) {
        remove(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    /** The pack finished loading (or failed): one tick later the tracker knows, and the view swaps sets if needed. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            View v = views.get(player.getUniqueId());
            if (player.isOnline() && v != null && v.withPack != hasPack.test(player)) {
                show(player, false);
            }
        });
    }

    /** A chunk with one of our buildings unloads: that owner's view goes; the next change rebuilds it quietly. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        Set<UUID> hit = new HashSet<>();
        for (Entity e : event.getEntities()) {
            UUID owner = owners.get(e.getUniqueId());
            if (owner != null) {
                hit.add(owner);
            }
        }
        hit.forEach(this::remove);
    }

    /** Shown building entities right now (for the tester / debug). */
    public int entities() {
        return owners.size();
    }
}
