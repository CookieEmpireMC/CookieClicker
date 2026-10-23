package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.session.SessionListener;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.station.StationListener;
import dev.casperrs.cookieClicker.station.StationRegistry;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.vfx.PopMotion;
import dev.casperrs.cookieClicker.view.panel.BulkMode;
import dev.casperrs.cookieClicker.view.panel.BulkModes;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import dev.casperrs.cookieClicker.view.panel.PanelPages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Every player's station visuals (cookie, pedestal or basket, both panels, the upgrade shelf) with a proximity
 * lifecycle: a refresh every {@code refresh-ticks} spawns a view when its owner is loaded and within
 * {@code view-distance}, and removes it when they leave. Quit, world change, station removal, chunk unload, reload and
 * disable remove views at once; {@link #sweep(boolean)} removes leftovers. While any view exists a per-tick task
 * drives the cookie motion, price rolls and shelf flights. The 1 Hz panel values come from the bakery ticker
 * ({@link #refreshPanels}). Main thread only.
 */
public final class StationViews implements Listener, StationListener, SessionListener {

    /** Shared sweep tag of every plugin entity (also used by the vfx engine). */
    public static final String TAG = "cookieclicker";
    /** The owner-visibility self-heal runs on every n-th refresh (it calls {@code canSee} per entity). */
    private static final int HEAL_EVERY_REFRESHES = 5;

    private final Plugin plugin;
    private final Supplier<StationVisualSettings> settings;
    private final StationRegistry stations;
    private final SessionManager sessions;
    private final Predicate<Player> hasPack;
    private final Logger log;
    private final NamespacedKey entityKey;
    private final WarnOnce warnings;
    private final BulkModes modes = new BulkModes();
    private final PanelPages pages = new PanelPages();
    private final ViewIndex index = new ViewIndex();
    private final StationSpawner spawner;
    private final ShelfController shelf;
    private final CookieAnimator animator;
    private final PanelUpdater panels;
    private @Nullable BukkitTask refreshTask;
    private @Nullable BukkitTask animationTask;
    private long refreshes;
    private boolean running;

    public StationViews(Plugin plugin, Supplier<StationVisualSettings> settings, StationRegistry stations,
                        SessionManager sessions, Economy economy, Messages messages, Predicate<Player> hasPack) {
        this.plugin = plugin;
        this.settings = settings;
        this.stations = stations;
        this.sessions = sessions;
        this.hasPack = hasPack;
        this.log = plugin.getLogger();
        this.entityKey = new NamespacedKey(plugin, "station");
        this.warnings = new WarnOnce(log);
        this.spawner = new StationSpawner(plugin, settings, economy, messages, hasPack, entityKey, warnings);
        this.shelf = new ShelfController(settings, economy, spawner, index, warnings);
        this.animator = new CookieAnimator(settings);
        this.panels = new PanelUpdater(settings, economy, messages, modes, spawner::iconStack, pages);
    }

    /** Called when a bought upgrade's icon lands on its shelf slot. */
    public void setShelfLanding(BiConsumer<Player, Location> shelfLanding) {
        shelf.setLanding(shelfLanding);
    }

    /** Called when the owner's basket reaches a higher tier. */
    public void setBasketTierUp(BiConsumer<Player, Location> basketTierUp) {
        animator.setTierUp(basketTierUp);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        stations.addListener(this);
        sessions.addListener(this);
        startRefresh();
    }

    /** Reload: removes every view (layout and settings may have changed); the next refresh respawns them. */
    public void restart() {
        cancelTasks();
        removeAll();
        warnings.clear();
        animator.reset();
        if (running) {
            startRefresh();
        }
    }

    /** Disable. Runs before the sessions shut down and doesn't rely on {@code onUnloaded}, which shutdown skips. */
    public void shutdown() {
        cancelTasks();
        removeAll();
        if (running) {
            HandlerList.unregisterAll(this);
            stations.removeListener(this);
            sessions.removeListener(this);
            running = false;
        }
        sweep(false);
    }

    private void startRefresh() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1, settings.get().refreshTicks());
    }

    private void startAnimation() {
        if (animationTask == null && running) {
            animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::animationTick, 1, 1);
        }
    }

    private void cancelTasks() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    /**
     * Removes leftover station entities in loaded worlds (ours by PDC key, not part of a live view).
     *
     * @param anyTagged also remove everything else tagged {@value #TAG} (enable only, before the vfx engine starts,
     *                  so crash leftovers of effect entities go too)
     * @return how many were removed
     */
    public int sweep(boolean anyTagged) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(TAG) && !index.isTracked(e.getUniqueId())
                        && (anyTagged || e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE))) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("Removed " + removed + " leftover station entit" + (removed == 1 ? "y" : "ies"));
        }
        return removed;
    }

    public Optional<ClickTarget> target(UUID entityId) {
        return Optional.ofNullable(index.target(entityId));
    }

    @Nullable StationView view(UUID owner, String stationId) {
        return index.view(owner, stationId);
    }

    StationVisualSettings settings() {
        return settings.get();
    }

    public int trackedEntities() {
        return index.entityCount();
    }

    BulkMode mode(UUID player, PanelKind panel) {
        return modes.get(player, panel);
    }

    BulkMode cycleMode(Player player, PanelKind panel) {
        return modes.cycle(player.getUniqueId(), panel);
    }

    void setMode(Player player, PanelKind panel, BulkMode mode) {
        modes.set(player.getUniqueId(), panel, mode);
    }

    /** Scrolls a panel one row (-1 up, +1 down); false when it can't scroll that way or doesn't page. */
    boolean scroll(StationView view, PanelKind panel, CookiePouch pouch, int delta) {
        return panels.scroll(view, view.panel(panel), pouch, delta);
    }

    void launchToShelf(Player owner, StationView view, StationView.Row row, String upgradeId, CookiePouch pouch) {
        shelf.launch(owner, view, row, upgradeId, pouch);
    }

    void beforePop(StationView view) {
        animator.beforePop(view);
    }

    PopMotion popMotion(StationView view) {
        return animator.popMotion(view);
    }

    private void refresh() {
        StationVisualSettings s = settings.get();
        boolean heal = ++refreshes % HEAL_EVERY_REFRESHES == 0;
        for (UUID owner : index.owners()) {
            if (Bukkit.getPlayer(owner) == null) {
                removeOwner(owner); // went offline without a quit event reaching us
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player, s, heal);
        }
    }

    /** One player's proximity lifecycle: spawn, heal or remove their view at every station. */
    private void refresh(Player player, StationVisualSettings s, boolean heal) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        if (pouch == null) {
            removeOwner(player.getUniqueId()); // not loaded (yet, or the load failed): no UI to click
            return;
        }
        double maxSq = s.viewDistance() * s.viewDistance();
        Location at = player.getLocation();
        String world = player.getWorld().getKey().asString();
        for (Station station : stations.all()) {
            StationView view = index.view(player.getUniqueId(), station.id());
            boolean inRange = station.distanceSquared(world, at.getX(), at.getY(), at.getZ()) <= maxSq
                    && StationAnchors.chunksLoaded(player.getWorld(), station, s);
            if (!inRange) {
                if (view != null) {
                    remove(view);
                }
                continue;
            }
            // a pack change (e.g. the pack finished loading after spawn) swaps basket and block: respawn
            if (view != null && (!view.healthy() || view.withPack != hasPack.test(player))) {
                remove(view);
                view = null;
            }
            if (view == null) {
                spawn(player, station, s, pouch);
            } else if (heal) {
                heal(player, view);
            }
        }
    }

    private void spawn(Player owner, Station station, StationVisualSettings s, CookiePouch pouch) {
        StationView view = spawner.spawn(owner, station, s, pouch, animator.motion());
        if (view == null) {
            return;
        }
        index.register(view);
        startAnimation();
        try {
            update(owner, view, pouch, false);
        } catch (RuntimeException e) {
            remove(view);
            warnings.warn("spawn", "Could not fill the bakery view at " + station.id() + ": " + e);
        }
    }

    /** Values of the owner's panels, shelf and basket now; rows that didn't change send nothing, changed prices roll. */
    public void refreshPanels(Player player, CookiePouch pouch) {
        refreshPanels(player, pouch, false);
    }

    /** @param pulse a player action (purchase, toggle) changed the values: rolling rows also pulse */
    void refreshPanels(Player player, CookiePouch pouch, boolean pulse) {
        for (StationView v : List.copyOf(index.viewsOf(player.getUniqueId()))) {
            update(player, v, pouch, pulse);
        }
    }

    private void update(Player owner, StationView view, CookiePouch pouch, boolean pulse) {
        long now = Bukkit.getCurrentTick();
        shelf.sync(owner, view, pouch);
        panels.update(view, pouch, now, pulse);
        animator.updateBasket(owner, view, pouch, now);
    }

    /** The owner should always see their own view. */
    private void heal(Player owner, StationView view) {
        for (Entity e : view.entities()) {
            if (!owner.canSee(e)) {
                owner.showEntity(plugin, e);
            }
        }
    }

    private void animationTick() {
        if (index.isEmpty()) {
            if (animationTask != null) {
                animationTask.cancel();
                animationTask = null;
            }
            return;
        }
        long now = Bukkit.getCurrentTick();
        // no copies: segments, roll steps and shelf flights never add or remove views
        for (Map<String, StationView> views : index.byOwner()) {
            for (StationView v : views.values()) {
                animator.animate(v, now);
                panels.animate(v, now);
                shelf.animate(v, now);
            }
        }
    }

    private void remove(StationView view) {
        index.unregister(view);
        view.remove();
    }

    private void removeOwner(UUID owner) {
        List.copyOf(index.viewsOf(owner)).forEach(this::remove);
    }

    private void removeStation(String stationId) {
        for (UUID owner : index.owners()) {
            StationView v = index.view(owner, stationId);
            if (v != null) {
                remove(v);
            }
        }
    }

    private void removeAll() {
        for (UUID owner : index.owners()) {
            removeOwner(owner);
        }
    }

    @Override
    public void onStationAdded(Station station) {
        // the next refresh spawns it for players in range
    }

    @Override
    public void onStationRemoved(Station station) {
        removeStation(station.id());
    }

    /** Spawns the bakery as soon as the session loads instead of on the next refresh. */
    @Override
    public void onLoaded(UUID player, CookiePouch pouch) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            refresh(online, settings.get(), false);
        }
    }

    @Override
    public void onUnloaded(UUID player) {
        removeOwner(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removeOwner(event.getPlayer().getUniqueId());
        modes.forget(event.getPlayer().getUniqueId());
        pages.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeOwner(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to.getWorld() != null && !to.getWorld().equals(event.getFrom().getWorld())) {
            removeOwner(event.getPlayer().getUniqueId());
        }
    }

    /** A chunk with one of our entities unloads: drop that whole view; the refresh respawns it when back in range. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        if (!index.hasEntities()) {
            return;
        }
        Set<StationView> hit = new HashSet<>();
        for (Entity e : event.getEntities()) {
            StationView v = index.byEntity(e.getUniqueId());
            if (v != null) {
                hit.add(v);
            }
        }
        hit.forEach(this::remove);
    }

    /** Non-persistent entities never come back from disk; anything of ours that does is a leftover. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            // the tag check is cheap and filters out almost every entity before the PDC is touched
            if (e.getScoreboardTags().contains(TAG)
                    && e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE)
                    && !index.isTracked(e.getUniqueId())) {
                e.remove();
            }
        }
    }
}
