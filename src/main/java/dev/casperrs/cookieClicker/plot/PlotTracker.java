package dev.casperrs.cookieClicker.plot;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Which plot / house every online player is in. Checked through {@link PlotRegions} (WorldGuard's cached
 * {@code RegionQuery}) only when a player changes block, teleports, respawns or joins, never on every move event.
 * Fires {@link PlotPresenceListener} on changes. Main thread only.
 */
public final class PlotTracker implements Listener {

    private final Plugin plugin;
    private final PlotRegistry plots;
    private final Map<UUID, Presence> current = new HashMap<>();
    private final List<PlotPresenceListener> listeners = new CopyOnWriteArrayList<>();
    private boolean running;

    public PlotTracker(Plugin plugin, PlotRegistry plots) {
        this.plugin = plugin;
        this.plots = plots;
    }

    public void addListener(PlotPresenceListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PlotPresenceListener listener) {
        listeners.remove(listener);
    }

    /** Where the player was at their last block change ({@link Presence#NONE} when unknown or offline). */
    public Presence presence(UUID player) {
        return current.getOrDefault(player, Presence.NONE);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        resyncAll();
    }

    /** After {@code /bakery reload}: regions or plots may have moved, re-check everyone and fire the difference. */
    public void resyncAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            update(p, p.getLocation());
        }
    }

    /** Disable: forget everyone without firing (listeners clean up in their own shutdown). */
    public void shutdown() {
        if (running) {
            HandlerList.unregisterAll(this);
            running = false;
        }
        current.clear();
    }

    private void update(Player player, Location to) {
        World world = to.getWorld();
        Presence next = world == null ? Presence.NONE
                : known(plots.regions().at(world, to.getBlockX(), to.getBlockY(), to.getBlockZ()),
                        to.getBlockX(), to.getBlockY(), to.getBlockZ());
        Presence before = current.getOrDefault(player.getUniqueId(), Presence.NONE);
        if (next.equals(before)) {
            return;
        }
        if (next.equals(Presence.NONE)) {
            current.remove(player.getUniqueId());
        } else {
            current.put(player.getUniqueId(), next);
        }
        fire(player, before, next);
    }

    /** Ignores flag values that name no plot in plots.yml. */
    private Presence known(Presence p, int x, int y, int z) {
        String plot = p.plot() != null && plots.has(p.plot()) ? p.plot() : null;
        String house = p.house() != null && plots.has(p.house()) ? p.house() : null;
        Plot.Box box = plot == null ? null : plots.get(plot).map(Plot::house).orElse(null);
        if (box != null) { // a set-up plot: its house box replaces the bakery-house region
            house = box.contains(x, y, z) ? plot : null;
        }
        return plot == null && house == null ? Presence.NONE : new Presence(plot, house);
    }

    private void fire(Player player, Presence before, Presence next) {
        if (before.house() != null && !Objects.equals(before.house(), next.house())) {
            each(l -> l.onLeaveHouse(player, before.house()));
        }
        if (before.plot() != null && !Objects.equals(before.plot(), next.plot())) {
            each(l -> l.onLeavePlot(player, before.plot()));
        }
        if (next.plot() != null && !Objects.equals(before.plot(), next.plot())) {
            each(l -> l.onEnterPlot(player, next.plot()));
        }
        if (next.house() != null && !Objects.equals(before.house(), next.house())) {
            each(l -> l.onEnterHouse(player, next.house()));
        }
    }

    private void each(java.util.function.Consumer<PlotPresenceListener> call) {
        for (PlotPresenceListener l : listeners) {
            try {
                call.accept(l);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Plot presence listener failed", e);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            update(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        update(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        update(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Presence before = current.remove(player.getUniqueId());
        if (before != null) {
            fire(player, before, Presence.NONE);
        }
    }
}
