package dev.casperrs.cookieClicker.house;

import dev.casperrs.cookieClicker.storage.HouseSeenStore;
import dev.casperrs.cookieClicker.storage.StorageException;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Async "has this player seen plot X's house cinematic" (schema v2 {@code house_seen}). Store calls run on the single
 * storage thread (ordered with the pouch loads/saves); callbacks run on the main thread. No Bukkit types.
 */
public final class HouseSeen {

    private final HouseSeenStore store;
    private final ExecutorService io;
    private final Executor main;
    private final Logger log;

    public HouseSeen(HouseSeenStore store, ExecutorService io, Executor main, Logger log) {
        this.store = store;
        this.io = io;
        this.main = main;
        this.log = log;
    }

    /**
     * Calls {@code result} on the main thread. If storage fails the answer is {@code true} (logged): better to skip a
     * cinematic than to replay it on every visit.
     */
    public void isSeen(UUID player, String plotId, Consumer<Boolean> result) {
        submit("read", player, () -> {
            boolean seen;
            try {
                seen = store.isHouseSeen(player, plotId);
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Could not read house_seen of " + player + " for plot " + plotId
                        + "; treating it as seen", e);
                seen = true;
            }
            boolean answer = seen;
            main.execute(() -> result.accept(answer));
        });
    }

    /** Only when the cinematic <b>completed</b>. Fire and forget. */
    public void markSeen(UUID player, String plotId) {
        submit("write", player, () -> {
            try {
                store.markHouseSeen(player, plotId);
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Could not save house_seen of " + player + " for plot " + plotId, e);
            }
        });
    }

    /** {@code /bakery house rebuild}: forget it, then {@code done} runs on the main thread (also after a failure). */
    public void clear(UUID player, String plotId, Runnable done) {
        submit("clear", player, () -> {
            try {
                store.clearHouseSeen(player, plotId);
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Could not clear house_seen of " + player + " for plot " + plotId, e);
            }
            main.execute(done);
        });
    }

    private void submit(String what, UUID player, Runnable task) {
        if (io.isShutdown()) {
            log.warning("Storage thread already stopped; house_seen " + what + " for " + player + " skipped");
            return;
        }
        try {
            io.execute(task);
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Could not queue house_seen " + what + " for " + player, e);
        }
    }
}
