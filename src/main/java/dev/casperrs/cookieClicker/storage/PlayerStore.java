package dev.casperrs.cookieClicker.storage;

import dev.casperrs.cookieClicker.economy.PouchSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * Blocking player-data storage. Implementations are <b>not thread-safe</b> and do I/O: call them only
 * from the single storage thread ({@link dev.casperrs.cookieClicker.session.SessionManager} does that).
 */
public interface PlayerStore extends AutoCloseable {

    /** Opens the connection and creates/migrates the schema. */
    void init() throws StorageException;

    /** Empty when the player has no saved data yet. Repairs bad values (see {@link LoadedPouch#problems()}). */
    Optional<LoadedPouch> load(UUID player) throws StorageException;

    void save(UUID player, PouchSnapshot snapshot) throws StorageException;

    @Override
    void close();
}
