package dev.casperrs.cookieClicker.storage;

import dev.casperrs.cookieClicker.economy.PouchSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Blocking player-data storage. Implementations are <b>not thread-safe</b> and do I/O: call them only
 * from the single storage thread ({@link dev.casperrs.cookieClicker.session.SessionManager} does that).
 */
public interface PlayerStore extends AutoCloseable {

    /** Creates/migrates the schema. */
    void init() throws StorageException;

    /** Empty when the player has no saved data yet. Repairs bad values (see {@link LoadedPouch#problems()}). */
    Optional<LoadedPouch> load(UUID player) throws StorageException;

    /** Autosave: may skip writes when nothing changed since this store's last save of the player. */
    void save(UUID player, PouchSnapshot snapshot) throws StorageException;

    /** The last save of a session (quit, disable): always writes. */
    default void saveFinal(UUID player, PouchSnapshot snapshot) throws StorageException {
        save(player, snapshot);
    }

    /** The player's current name, written with their next load or save (for the website). */
    default void rememberName(UUID player, String name) {
    }

    @Override
    void close();
}
