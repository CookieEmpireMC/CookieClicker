package dev.casperrs.cookieClicker.storage;

import java.util.UUID;

/**
 * Whether a player has seen a plot's house-building cinematic ({@code <prefix>house_seen}, schema v2). Blocking and
 * not thread-safe like {@link PlayerStore}: call only from the single storage thread
 * ({@link dev.casperrs.cookieClicker.house.HouseSeen} does that).
 */
public interface HouseSeenStore {

    boolean isHouseSeen(UUID player, String plotId) throws StorageException;

    /** Idempotent: a second call keeps the first {@code seen_at}. */
    void markHouseSeen(UUID player, String plotId) throws StorageException;

    /** For {@code /bakery house rebuild}: the next visit plays the cinematic again. */
    void clearHouseSeen(UUID player, String plotId) throws StorageException;
}
