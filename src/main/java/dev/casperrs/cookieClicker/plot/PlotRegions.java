package dev.casperrs.cookieClicker.plot;

import org.bukkit.World;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Read-only access to the plot/house regions. The only implementation that talks to WorldGuard lives in the
 * {@code worldguard} package; everything else (and the tests) uses this interface. Never keeps region objects: every
 * call reads the current regions, so {@code /rg redefine} is picked up by the next call (callers cache ids only).
 * Main thread only.
 */
public interface PlotRegions {

    /** A region of {@code world} with the values of our two flags (lower case, null when unset). */
    record RegionInfo(String id, int priority, @Nullable String plotFlag, @Nullable String houseFlag) {
    }

    /**
     * Which plot / house the block at {@code x y z} is in, by flag value. {@link Presence#NONE} when it is in none, or
     * when WorldGuard has no region data for the world.
     */
    Presence at(World world, int x, int y, int z);

    /** Every region of {@code world} (empty when WorldGuard has no region manager for it). */
    List<RegionInfo> regions(World world);

    /** Snapshot of a region's shape, empty when it doesn't exist or has no area (the global region). */
    Optional<RegionShape> shape(World world, String regionId);
}
