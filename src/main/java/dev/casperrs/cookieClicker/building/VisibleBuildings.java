package dev.casperrs.cookieClicker.building;

import dev.casperrs.cookieClicker.config.BuildingVisualSettings;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.house.design.BuildingSlots;
import dev.casperrs.cookieClicker.house.design.HouseDesign;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotPresenceListener;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotTracker;
import dev.casperrs.cookieClicker.session.SessionListener;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.vfx.Effects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.jspecify.annotations.Nullable;

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
 * The bought bakery buildings on the factory plot. Plots are one shared location, so every player in a plot sees
 * only their own buildings: instance <i>i</i> of a building stands on slot <i>i</i> of
 * {@code houses/building-slots.yml}, up to its slot count. A plot's layout frame is worked back from the house box its
 * setup stored ({@link BuildingLayout#origin}); plots without a setup show nothing.
 *
 * <p>Updated only on a change (purchase, session load, plot enter/leave, pack status, plot setup, reload); no task.
 * Only a purchase is loud: new instances play {@code building_appear}, a stage change plays
 * {@code building_milestone} on every shown instance of that type, staggered, with at most one sound. Main thread.
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
    private final WarnOnce warnings;
    private final BuildingDisplays displays;

    private BuildingSlotsLoader.Loaded loaded = BuildingSlotsLoader.Loaded.EMPTY;
    private final Map<UUID, View> views = new HashMap<>();
    /** Entity id → owner, for chunk unloads. */
    private final Map<UUID, UUID> owners = new HashMap<>();
    private boolean running;

    /** One player's buildings in the plot they are in. */
    private static final class View {
        final String plotId;
        final World world;
        final int[] origin;
        final boolean withPack;
        final Map<String, List<Display>> shown = new HashMap<>();
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

    /** The effects and the one sound of a purchase. */
    private static final class Feedback {
        final List<Play> plays = new ArrayList<>();
        @Nullable String sound;
        @Nullable Location soundAt;

        void appeared(Display d) {
            plays.add(new Play(EFFECT_APPEAR, d.getLocation()));
            if (sound == null) {
                sound = SOUND_APPEAR;
                soundAt = d.getLocation();
            }
        }

        void milestone(List<Display> shown) {
            for (Display d : shown) {
                plays.add(new Play(EFFECT_MILESTONE, d.getLocation()));
            }
            sound = SOUND_MILESTONE; // the bigger moment wins the one sound
            soundAt = shown.getFirst().getLocation();
        }
    }

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
        this.warnings = new WarnOnce(plugin.getLogger());
        this.displays = new BuildingDisplays(plugin, warnings);
    }

    /** Enable: reads the slots, hooks in and shows everyone already in a plot (quietly). */
    public void start() {
        if (!running) {
            running = true;
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            tracker.addListener(this);
            sessions.addListener(this);
        }
        loaded = BuildingSlotsLoader.load(plugin);
        showAll();
    }

    /** {@code /bakery reload}, after the plots were re-read and presence re-synced: rebuilt quietly. */
    public void reload() {
        removeAll();
        warnings.clear();
        loaded = BuildingSlotsLoader.load(plugin);
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

    private void showAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            show(p, false);
        }
    }

    /**
     * Brings {@code player}'s buildings in line with their pouch and the plot they are in. {@code loud} plays the
     * appear/milestone effects and one sound (a purchase only; never for a fresh view).
     */
    private void show(Player player, boolean loud) {
        UUID id = player.getUniqueId();
        BuildingVisualSettings s = settings.get();
        CookiePouch pouch = sessions.get(id).orElse(null);
        View view = pouch == null ? null : views.get(id);
        if (pouch == null || !isCurrent(player, view, s)) {
            remove(id);
            view = pouch == null ? null : newView(player, s);
            loud = false;
        }
        if (view == null) {
            return;
        }
        Feedback feedback = loud ? new Feedback() : null;
        try {
            for (Map.Entry<String, List<BuildingSlots.Slot>> e : loaded.slots().slots().entrySet()) {
                update(player, view, e.getKey(), e.getValue(), pouch.owned(e.getKey()), s, feedback);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not show the buildings of " + player.getName(), ex);
            remove(id);
            return;
        }
        if (feedback != null) {
            playFeedback(player, feedback, s);
        }
    }

    /** Where the player's buildings stand right now; null when nothing is shown there. */
    private @Nullable View target(Player player, BuildingVisualSettings s) {
        String plotId = tracker.presence(player.getUniqueId()).plot();
        Plot plot = plotId == null ? null : plots.get(plotId).orElse(null);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        HouseDesign design = loaded.design();
        int[] origin = plot == null || design == null ? null : BuildingLayout.origin(plot, design);
        if (!s.enabled() || world == null || origin == null || loaded.slots().slots().isEmpty()) {
            return null;
        }
        return new View(plotId, world, origin, hasPack.test(player));
    }

    private boolean isCurrent(Player player, @Nullable View view, BuildingVisualSettings s) {
        if (view == null) {
            return false;
        }
        View now = target(player, s);
        return now != null && view.plotId.equals(now.plotId) && view.withPack == now.withPack
                && view.world.equals(now.world);
    }

    private @Nullable View newView(Player player, BuildingVisualSettings s) {
        View view = target(player, s);
        if (view != null) {
            views.put(player.getUniqueId(), view);
        }
        return view;
    }

    /** One building type: drop extra instances, swap the stage model, spawn the missing ones. */
    private void update(Player player, View view, String building, List<BuildingSlots.Slot> buildingSlots, int owned,
                        BuildingVisualSettings s, @Nullable Feedback feedback) {
        Integer before = view.owned.put(building, owned);
        BuildingLayout.Change c = BuildingLayout.change(before == null ? -1 : before, owned, buildingSlots.size(), s);
        List<Display> shown = view.shown.computeIfAbsent(building, k -> new ArrayList<>());
        while (shown.size() > c.shownAfter()) { // only after a balance/slot reload: there is no selling
            Display d = shown.removeLast();
            owners.remove(d.getUniqueId());
            d.remove();
        }
        if (c.stageChanged() && view.withPack) {
            ItemStack stack = displays.modelStack(building, c.stageAfter(), s);
            for (Display d : shown) {
                if (d instanceof ItemDisplay item) {
                    item.setItemStack(stack);
                }
            }
        }
        for (int i = shown.size(); i < c.shownAfter(); i++) {
            Display d = displays.spawn(player, view.world, view.origin, view.withPack, building, buildingSlots.get(i),
                    c.stageAfter(), s);
            if (d == null) {
                break; // chunk not loaded: the rest shows on the next change
            }
            owners.put(d.getUniqueId(), player.getUniqueId());
            shown.add(d);
            if (feedback != null) {
                feedback.appeared(d);
            }
        }
        if (feedback != null && c.stageChanged() && !shown.isEmpty()) {
            feedback.milestone(shown);
        }
    }

    private void playFeedback(Player player, Feedback feedback, BuildingVisualSettings s) {
        if (feedback.sound != null) {
            sounds.play(player, feedback.sound, feedback.soundAt);
        }
        // one purchase may cross a stage for many types at once: cap its effects
        List<Play> plays = feedback.plays.subList(0, Math.min(feedback.plays.size(), s.maxEffectsPerPurchase()));
        int stagger = s.effectStaggerTicks();
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

    /** One tick later the pack tracker knows the new status; the view swaps sets if needed. */
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
        if (owners.isEmpty()) {
            return;
        }
        Set<UUID> hit = new HashSet<>();
        for (Entity e : event.getEntities()) {
            UUID owner = owners.get(e.getUniqueId());
            if (owner != null) {
                hit.add(owner);
            }
        }
        hit.forEach(this::remove);
    }

    /** Shown building entities right now (debug). */
    public int entities() {
        return owners.size();
    }
}
