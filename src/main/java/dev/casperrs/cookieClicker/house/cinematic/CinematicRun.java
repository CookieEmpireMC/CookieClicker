package dev.casperrs.cookieClicker.house.cinematic;

import org.bukkit.World;

import java.util.Set;
import java.util.UUID;

/** One player's running build cinematic. Main thread only. */
final class CinematicRun {

    final UUID player;
    final String plotId;
    final World world;
    final HouseBox box;
    final RevealPlan plan;
    /** Packed positions of blocks with a block entity (they need their data resent when revealed). */
    final Set<Long> tiles;
    /** Room for one tick's hides plus a full re-assert. */
    final long[] sendBuffer;
    int impacts;
    boolean alive = true;
    /** Watched from the camera point in spectator mode. */
    final boolean camera;

    CinematicRun(UUID player, String plotId, World world, HouseBox box, RevealPlan plan, Set<Long> tiles,
                 boolean camera) {
        this.player = player;
        this.plotId = plotId;
        this.world = world;
        this.box = box;
        this.plan = plan;
        this.tiles = tiles;
        this.camera = camera;
        this.sendBuffer = new long[2 * plan.size()];
    }
}
