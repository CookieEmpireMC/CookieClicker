package dev.casperrs.cookieClicker.plot;

import dev.casperrs.cookieClicker.config.PlotSettings;
import dev.casperrs.cookieClicker.vfx.Effects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /bakery plot show}: replays the plot and house outlines to one admin for a while. Particles go through
 * {@link Effects} only (presets {@code plot.show.plot-effect} / {@code house-effect}). The outline points are computed
 * once per show from a fresh region snapshot. Stopped on quit, reload and disable. Main thread only.
 */
public final class PlotOutlines implements Listener {

    private final Plugin plugin;
    private final Effects effects;
    private final Supplier<PlotSettings.Show> settings;
    private final Map<UUID, BukkitTask> running = new HashMap<>();
    private boolean registered;

    public PlotOutlines(Plugin plugin, Effects effects, Supplier<PlotSettings.Show> settings) {
        this.plugin = plugin;
        this.effects = effects;
        this.settings = settings;
    }

    /**
     * Starts (or restarts) the outline for {@code viewer}.
     */
    public void show(Player viewer, World world, RegionShape plot, @Nullable RegionShape house) {
        PlotSettings.Show s = settings.get();
        showPoints(viewer, world, Outline.points(plot, s.particleStep(), s.maxPoints()),
                house == null ? List.of() : Outline.points(house, s.particleStep(), s.maxPoints()));
    }

    /**
     * Starts (or restarts) an outline from ready points: {@code outline} with the plot effect, {@code marks} with the
     * house effect ({@code /bakery farm show}).
     */
    public void showPoints(Player viewer, World world, List<double[]> plotPoints, List<double[]> housePoints) {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        stop(viewer.getUniqueId());
        PlotSettings.Show s = settings.get();
        UUID id = viewer.getUniqueId();
        long until = Bukkit.getCurrentTick() + s.durationSeconds() * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.getWorld().equals(world) || Bukkit.getCurrentTick() > until) {
                stop(id);
                return;
            }
            play(p, world, plotPoints, s.plotEffect());
            play(p, world, housePoints, s.houseEffect());
        }, 0, s.redrawTicks());
        running.put(id, task);
    }

    private void play(Player viewer, World world, List<double[]> points, String effect) {
        for (double[] pt : points) {
            // a new Location each time: effect jobs may keep it for several ticks
            effects.play(effect, new Location(world, pt[0], pt[1], pt[2]), viewer);
        }
    }

    public void stop(UUID viewer) {
        BukkitTask task = running.remove(viewer);
        if (task != null) {
            task.cancel();
        }
    }

    /** Reload and disable. */
    public void stopAll() {
        running.values().forEach(BukkitTask::cancel);
        running.clear();
    }

    public void shutdown() {
        stopAll();
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }
}
