package dev.casperrs.cookieClicker.session;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.PouchSnapshot;
import dev.casperrs.cookieClicker.storage.LoadedPouch;
import dev.casperrs.cookieClicker.storage.PlayerStore;
import dev.casperrs.cookieClicker.storage.StorageException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Online players' {@link CookiePouch}es and their async load/save. No Bukkit types, so it can be tested
 * with a direct executor for {@code main} and a single-thread executor for {@code io}.
 *
 * <p>Threading: every public method runs on the main thread. All {@link PlayerStore} calls run on
 * {@code io}, which must be a <b>single</b> thread: that keeps a quit-save ordered before the load of a
 * quick rejoin. Results hop back via {@code main}.
 *
 * <p>If loading fails, the player gets no session (so a broken read can never overwrite their real save)
 * and listeners hear {@link SessionListener#onLoadFailed}.
 */
public final class SessionManager {

    private final PlayerStore store;
    private final ExecutorService io;
    private final Executor main;
    private final Logger log;
    private final Map<UUID, CookiePouch> sessions = new HashMap<>();
    /** Loads in flight; the token detects a quit (and maybe rejoin) before the load finished. */
    private final Map<UUID, Object> pending = new HashMap<>();
    private final List<SessionListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean storageReady;

    public SessionManager(PlayerStore store, ExecutorService io, Executor main, Logger log) {
        this.store = store;
        this.io = io;
        this.main = main;
        this.log = log;
    }

    public void addListener(SessionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(SessionListener listener) {
        listeners.remove(listener);
    }

    /** Queues schema init on the storage thread; loads queued after it wait for it automatically. */
    public void start() {
        io.execute(() -> {
            try {
                store.init();
                storageReady = true;
            } catch (StorageException e) {
                log.log(Level.SEVERE, "Storage is unavailable; players will not be able to play until it is fixed", e);
            }
        });
    }

    public Optional<CookiePouch> get(UUID player) {
        return Optional.ofNullable(sessions.get(player));
    }

    public boolean isLoaded(UUID player) {
        return sessions.containsKey(player);
    }

    public Collection<UUID> loadedPlayers() {
        return Collections.unmodifiableSet(sessions.keySet());
    }

    /** Player joined: load async, then create the session on the main thread. */
    public void join(UUID player) {
        if (sessions.containsKey(player) || pending.containsKey(player)) {
            return;
        }
        Object token = new Object();
        pending.put(player, token);
        io.execute(() -> {
            Optional<LoadedPouch> loaded;
            try {
                if (!storageReady) {
                    throw new StorageException("storage did not initialise");
                }
                loaded = store.load(player);
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Could not load cookies for " + player, e);
                main.execute(() -> {
                    if (pending.remove(player, token)) {
                        listeners.forEach(l -> l.onLoadFailed(player));
                    }
                });
                return;
            }
            loaded.ifPresent(l -> l.problems().forEach(problem ->
                    log.warning("Repaired save of " + player + ": " + problem)));
            main.execute(() -> {
                if (!pending.remove(player, token)) {
                    return; // quit before the load finished
                }
                CookiePouch pouch = loaded.map(l -> CookiePouch.fromSnapshot(l.snapshot())).orElseGet(CookiePouch::new);
                sessions.put(player, pouch);
                listeners.forEach(l -> l.onLoaded(player, pouch));
            });
        });
    }

    /** Player quit: drop the session and save it async. */
    public void quit(UUID player) {
        pending.remove(player);
        CookiePouch pouch = sessions.remove(player);
        if (pouch != null) {
            listeners.forEach(l -> l.onUnloaded(player));
            saveAsync(player, pouch.snapshot());
        }
    }

    /** Autosave: snapshot every session now (main thread), write async. */
    public void saveAll() {
        sessions.forEach((player, pouch) -> saveAsync(player, pouch.snapshot()));
    }

    private void saveAsync(UUID player, PouchSnapshot snapshot) {
        if (io.isShutdown()) {
            log.severe("Storage thread already stopped; could not save " + player);
            return;
        }
        io.execute(() -> {
            try {
                store.save(player, snapshot);
            } catch (StorageException | RuntimeException e) {
                log.log(Level.SEVERE, "Could not save cookies for " + player, e);
            }
        });
    }

    /**
     * Plugin disable: save everyone, then wait (blocking, up to {@code timeoutSeconds}) for every queued
     * write to finish and close the store. Callbacks to {@code main} after this are dropped by the caller's
     * executor (the plugin is disabled).
     */
    public void shutdown(long timeoutSeconds) {
        saveAll();
        sessions.clear();
        pending.clear();
        io.execute(store::close);
        io.shutdown();
        try {
            if (!io.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                log.severe("Saving did not finish within " + timeoutSeconds + " s; some progress may be lost");
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            io.shutdownNow();
        }
    }
}
