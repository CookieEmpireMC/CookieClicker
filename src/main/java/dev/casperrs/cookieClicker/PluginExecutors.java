package dev.casperrs.cookieClicker;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The plugin's two executors: one storage thread and a hop back to the main thread. */
final class PluginExecutors {

    private PluginExecutors() {
    }

    /** One storage thread: DB loads/saves, plots.yml/stations.yml writes and house backups, in order. */
    static ExecutorService io() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CookieClicker-IO");
            t.setDaemon(true);
            return t;
        });
    }

    /** Runs a task on the next tick; drops it once the plugin is shutting down (sessions are already flushed). */
    static Executor main(JavaPlugin plugin) {
        return task -> {
            if (!plugin.isEnabled()) {
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin, task);
            } catch (IllegalPluginAccessException e) {
                plugin.getLogger().fine("Dropped a storage callback during shutdown");
            }
        };
    }
}
