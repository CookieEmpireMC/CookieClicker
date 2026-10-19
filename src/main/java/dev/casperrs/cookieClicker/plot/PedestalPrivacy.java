package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.config.PlotSettings;
import dev.casperrs.cookieClicker.station.Station;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Optional {@code plot.hide-others-near-pedestal}: two players who both stand within {@code radius} of the same
 * pedestal don't see each other, so everyone's own cookie and shop stay uncluttered. Uses plugin-scoped
 * {@code hidePlayer}/{@code showPlayer} and only ever shows players it hid itself. Checked every
 * {@code check-ticks}; everything is shown again on quit, reload (when disabled) and disable. Main thread only.
 */
public final class PedestalPrivacy implements Listener {

    /** A player hiding another player. */
    public record Pair(UUID viewer, UUID hidden) {
    }

    /** Position of a player for {@link #pairs}. */
    public record Pos(UUID id, String worldKey, double x, double y, double z) {
    }

    private final Plugin plugin;
    private final Supplier<PlotSettings.HideOthers> settings;
    private final Supplier<Collection<Station>> pedestals;
    private final Set<Pair> hidden = new HashSet<>();
    private @Nullable BukkitTask task;
    private boolean registered;

    public PedestalPrivacy(Plugin plugin, Supplier<PlotSettings.HideOthers> settings,
                           Supplier<Collection<Station>> pedestals) {
        this.plugin = plugin;
        this.settings = settings;
        this.pedestals = pedestals;
    }

    /** Enable and reload: (re)starts the check when enabled, else shows everyone again. */
    public void restart() {
        stopTask();
        PlotSettings.HideOthers s = settings.get();
        if (!s.enabled()) {
            showAll();
            return;
        }
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 1, s.checkTicks());
    }

    public void shutdown() {
        stopTask();
        showAll();
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void check() {
        List<Pos> players = new ArrayList<>();
        Map<UUID, Player> online = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            players.add(new Pos(p.getUniqueId(), p.getWorld().getKey().asString(), l.getX(), l.getY(), l.getZ()));
            online.put(p.getUniqueId(), p);
        }
        Set<Pair> wanted = pairs(players, pedestals.get(), settings.get().radius());
        for (Pair pair : List.copyOf(hidden)) {
            if (!wanted.contains(pair)) {
                show(pair, online);
            }
        }
        for (Pair pair : wanted) {
            if (hidden.add(pair)) {
                Player viewer = online.get(pair.viewer());
                Player other = online.get(pair.hidden());
                if (viewer != null && other != null) {
                    viewer.hidePlayer(plugin, other);
                }
            }
        }
    }

    /** Pure: every ordered pair of different players within {@code radius} of the same pedestal. */
    public static Set<Pair> pairs(Collection<Pos> players, Collection<Station> pedestals, double radius) {
        Set<Pair> out = new HashSet<>();
        double r2 = radius * radius;
        for (Station s : pedestals) {
            List<UUID> near = new ArrayList<>();
            for (Pos p : players) {
                if (s.distanceSquared(p.worldKey(), p.x(), p.y(), p.z()) <= r2) {
                    near.add(p.id());
                }
            }
            for (UUID a : near) {
                for (UUID b : near) {
                    if (!a.equals(b)) {
                        out.add(new Pair(a, b));
                    }
                }
            }
        }
        return out;
    }

    private void show(Pair pair, Map<UUID, Player> online) {
        hidden.remove(pair);
        Player viewer = online.get(pair.viewer());
        Player other = online.get(pair.hidden());
        if (viewer != null && other != null) {
            viewer.showPlayer(plugin, other);
        }
    }

    private void showAll() {
        Map<UUID, Player> online = new HashMap<>();
        Bukkit.getOnlinePlayers().forEach(p -> online.put(p.getUniqueId(), p));
        for (Pair pair : List.copyOf(hidden)) {
            show(pair, online);
        }
        hidden.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Map<UUID, Player> online = new HashMap<>();
        Bukkit.getOnlinePlayers().forEach(p -> online.put(p.getUniqueId(), p));
        for (Pair pair : List.copyOf(hidden)) {
            if (pair.viewer().equals(id) || pair.hidden().equals(id)) {
                show(pair, online); // plugin-hidden state is per session anyway; keep our set clean
            }
        }
    }
}
