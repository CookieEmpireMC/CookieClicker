package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.RegionShape;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/** Plans a run before it starts: which blocks are revealed in which order, and where the player watches from. */
final class RunPlanner {

    /** Where a run starts: the blocks to reveal and where the player watches from. */
    record Setup(World world, HouseBox box, RevealPlan plan, Set<Long> tiles, Location feet,
                         @Nullable Location camera, boolean teleport) {
    }

    private final FakeBlocks fakeBlocks;
    private final Supplier<HouseSettings> settings;

    RunPlanner(FakeBlocks fakeBlocks, Supplier<HouseSettings> settings) {
        this.fakeBlocks = fakeBlocks;
        this.settings = settings;
    }

    static boolean loaded(World world, HouseBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Only blocks that are really there (air stays air); null when there are none. */
    @Nullable RevealPlan plan(World world, HouseShapes.Shape shape) {
        long[] blocks = new long[shape.blocks().length];
        int n = 0;
        for (long p : shape.blocks()) {
            if (!world.getType(RegionShape.unpackX(p), RegionShape.unpackY(p), RegionShape.unpackZ(p)).isAir()) {
                blocks[n++] = p;
            }
        }
        if (n == 0) {
            return null;
        }
        HouseSettings.Cinematic c = settings.get().cinematic();
        return new RevealPlan(RevealOrder.of(Arrays.copyOf(blocks, n)), new RevealPlan.Pace(c.blocksPerTick(),
                c.maxFallingDisplays(), c.layerDelayTicks(), c.fakeAirPerTick(), c.refreshHiddenTicks()));
    }

    Setup setup(Player player, Plot plot, World world, HouseShapes.Shape shape, RevealPlan plan) {
        Set<Long> tiles = new HashSet<>();
        for (int i = 0; i < plan.size(); i++) {
            long p = plan.position(i);
            if (fakeBlocks.isTile(world, RegionShape.unpackX(p), RegionShape.unpackY(p), RegionShape.unpackZ(p))) {
                tiles.add(p);
            }
        }
        HouseSettings.Cinematic c = settings.get().cinematic();
        Plot.Viewpoint cam = c.camera() ? plot.camera() : null;
        Plot.Viewpoint v = c.teleportToViewpoint() || cam != null ? plot.viewpoint() : null;
        Location feet = v != null ? new Location(world, v.x(), v.y(), v.z(), v.yaw(), v.pitch()) : player.getLocation();
        Location cameraAt = cam == null ? null : new Location(world, cam.x(), cam.y(), cam.z(), cam.yaw(), cam.pitch());
        return new Setup(world, shape.box(), plan, tiles, feet, cameraAt, v != null);
    }
}
