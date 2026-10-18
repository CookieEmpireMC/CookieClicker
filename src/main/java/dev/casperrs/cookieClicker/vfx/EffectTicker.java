package dev.casperrs.cookieClicker.vfx;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

/**
 * One shared main-thread task that steps every running {@link Job}. It only runs while there is work: it starts
 * on the first job and cancels itself when the last one ends. {@link #cancelAll()} stops everything (shutdown and
 * reload).
 */
final class EffectTicker {

    private final Plugin plugin;
    private final List<Job> jobs = new ArrayList<>();
    private @Nullable BukkitTask task;
    private boolean warned;

    EffectTicker(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Runs the job's first tick right now; keeps it if it has more ticks to go. */
    void start(Job job) {
        if (safeStep(job)) {
            jobs.add(job);
            if (task == null) {
                // the Runnable overload returns the task, so it can be cancelled on shutdown
                task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::run, 1L, 1L);
            }
        }
    }

    private void run() {
        for (int i = 0; i < jobs.size(); ) {
            if (safeStep(jobs.get(i))) {
                i++;
            } else {
                int last = jobs.size() - 1;
                jobs.set(i, jobs.get(last));
                jobs.remove(last);
            }
        }
        if (jobs.isEmpty()) {
            stopTask();
        }
    }

    private boolean safeStep(Job job) {
        try {
            return job.step();
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING, "An effect failed and was stopped (further errors hidden)", e);
            }
            try {
                job.cancel();
            } catch (RuntimeException ignored) {
                // already failing; nothing more to clean up safely
            }
            return false;
        }
    }

    void cancelAll() {
        List<Job> copy = new ArrayList<>(jobs);
        jobs.clear();
        for (Job job : copy) {
            try {
                job.cancel();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not clean up an effect", e);
            }
        }
        stopTask();
        warned = false;
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
