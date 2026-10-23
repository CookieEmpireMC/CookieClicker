package dev.casperrs.cookieClicker.belt;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** One player's cookies on the belt of the plot they are in. Main thread. */
final class BeltRider {

    /** One cookie: spawned at {@code spawnTick}, starts moving the tick after, arrives {@code rideTicks} later. */
    static final class Ride {
        final ItemDisplay entity;
        final long spawnTick;
        final int rideTicks;
        /** Model thickness of the item it shows (our 3D cookie or the vanilla sprite). */
        final double thickness;
        long arriveTick = Long.MAX_VALUE;

        Ride(ItemDisplay entity, long spawnTick, int rideTicks, double thickness) {
            this.entity = entity;
            this.spawnTick = spawnTick;
            this.rideTicks = rideTicks;
            this.thickness = thickness;
        }
    }

    final String plotId;
    final World world;
    final BeltPath path;
    final BeltLane lane = new BeltLane();
    final List<Ride> rides = new ArrayList<>();
    double cps;
    boolean withPack;
    private @Nullable CookiePouch cpsPouch;
    private @Nullable Object cpsConfig;
    private int cpsRevision = -1;

    BeltRider(String plotId, World world, BeltPath path) {
        this.plotId = plotId;
        this.world = world;
        this.path = path;
    }

    /**
     * Whether the pouch or the balance changed since {@link #cps} was computed (and remembers the new state).
     * Every building or upgrade level added bumps {@link CookiePouch#revision()}, so that shows a purchase.
     */
    boolean cpsChanged(CookiePouch pouch, Object config) {
        int revision = pouch.revision();
        if (pouch == cpsPouch && config == cpsConfig && revision == cpsRevision) {
            return false;
        }
        cpsPouch = pouch;
        cpsConfig = config;
        cpsRevision = revision;
        return true;
    }

    void clearRides() {
        rides.forEach(ride -> ride.entity.remove());
        rides.clear();
    }
}
