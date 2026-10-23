package dev.casperrs.cookieClicker.sound;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Replays looping ambient sounds ({@code house_chimney_crackle}, {@code factory_ambient}) for players on one shared
 * 1-tick task instead of one timer per player per sound. A loop's first play is due after
 * {@link SoundService#loopDelay} (so a restart keeps the old beat), the next one {@code period} ticks after the tick
 * it actually ran on (so {@link LoopPacer} never refuses it). On a due tick it plays only while {@code when} holds,
 * otherwise that beat is skipped. The task stops itself when no loop is left and starts again with the next one.
 * Main thread only.
 */
public final class LoopPlayer {

    private final SoundService sounds;
    private final IntSupplier ticks;
    private final Function<Runnable, @Nullable BukkitTask> everyTick;
    private final List<Loop> loops = new ArrayList<>();
    private @Nullable BukkitTask task;

    /** One registered loop; {@link #cancel()} removes it at the next tick. */
    public static final class Loop {
        private final Player player;
        private final String event;
        private final Location at;
        private final int period;
        private final BooleanSupplier when;
        private long due;
        private boolean cancelled;

        private Loop(Player player, String event, Location at, int period, BooleanSupplier when, long due) {
            this.player = player;
            this.event = event;
            this.at = at;
            this.period = period;
            this.when = when;
            this.due = due;
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean isActive() {
            return !cancelled;
        }
    }

    public LoopPlayer(Plugin plugin, SoundService sounds) {
        this(sounds, Bukkit::getCurrentTick, r -> Bukkit.getScheduler().runTaskTimer(plugin, r, 0L, 1L));
    }

    /** {@code everyTick} schedules the shared every-tick task (null in tests). */
    LoopPlayer(SoundService sounds, IntSupplier ticks, Function<Runnable, @Nullable BukkitTask> everyTick) {
        this.sounds = sounds;
        this.ticks = ticks;
        this.everyTick = everyTick;
    }

    /**
     * Starts replaying {@code event} to {@code player} at {@code at} every {@code period} ticks (the file length at
     * its fixed pitch), playing on a due tick only while {@code when} is true.
     */
    public Loop start(Player player, String event, Location at, int period, BooleanSupplier when) {
        long now = ticks.getAsInt();
        int every = Math.max(1, period);
        Loop loop = new Loop(player, event, at.clone(), every, when,
                now + sounds.loopDelay(player.getUniqueId(), event, every, now));
        loops.add(loop);
        if (task == null) {
            task = everyTick.apply(this::tick);
        }
        return loop;
    }

    /** Cancels every loop and stops the task. */
    public void stopAll() {
        loops.forEach(Loop::cancel);
        loops.clear();
        stopTask();
    }

    /** One server tick of the shared task: plays every loop that is due. */
    void tick() {
        long now = ticks.getAsInt();
        loops.removeIf(l -> l.cancelled);
        for (Loop l : loops) {
            if (now >= l.due) {
                l.due = now + l.period; // from the actual play, so LoopPacer never refuses the next beat
                if (l.when.getAsBoolean()) {
                    sounds.playLoop(l.player, l.event, l.at, l.period, now);
                }
            }
        }
        if (loops.isEmpty()) {
            stopTask();
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
