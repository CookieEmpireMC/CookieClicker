package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.vfx.config.LayerSpec;
import dev.casperrs.cookieClicker.vfx.shape.DropTrack;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The falling blocks of the house cinematic ({@link Effects#dropBlock}): one {@link DropJob} per block. */
final class BlockDrops {

    private static final LayerSpec.BlockDrop DEFAULT_DROP = new LayerSpec.BlockDrop("default:block-drop",
            DropTrack.of(DropTrack.Params.DEFAULTS), "house_block_land", 4, -1, -1, 1.0f);

    private final Effects effects;
    private final Spawner spawner;
    private final EffectTicker ticker;
    private final PresetLibrary library;
    private final WarnOnce warn;
    private final ViewerJobs<DropJob> jobs = new ViewerJobs<>();
    private final Set<UUID> entities = new HashSet<>();

    BlockDrops(Effects effects, Spawner spawner, EffectTicker ticker, PresetLibrary library, WarnOnce warn) {
        this.effects = effects;
        this.spawner = spawner;
        this.ticker = ticker;
        this.library = library;
        this.warn = warn;
    }

    boolean drop(Player viewer, BlockData data, Location target, @Nullable Runnable onImpact, Runnable onLanded) {
        if (!effects.onMainThread("dropBlock", null)) {
            return false;
        }
        World world = target.getWorld();
        if (world == null || !viewer.isOnline() || !viewer.getWorld().equals(world)) {
            return false;
        }
        int bx = target.getBlockX();
        int by = target.getBlockY();
        int bz = target.getBlockZ();
        LayerSpec.BlockDrop spec = spec();
        Runnable impact = () -> impact(spec, viewer, world, bx, by, bz, data, onImpact);
        UUID id = viewer.getUniqueId();
        boolean loaded = effects.isLoaded();
        if (!loaded) {
            warn.warn("not-loaded", "Effects.dropBlock() was called before Effects.load(); landing instantly");
        }
        if (!loaded || data.getMaterial().isAir() || !jobs.hasRoom(id,
                effects.limits().maxFallingBlocksPerPlayer(), effects.limits().maxFallingBlocksGlobal())) {
            return landInstantly(impact, onLanded);
        }
        BlockDisplay display;
        try {
            DropTrack track = spec.track();
            Display.Brightness light = spec.autoBrightness() ? lightAbove(world, bx, by, bz)
                    : new Display.Brightness(spec.blockLight(), spec.skyLight());
            display = spawner.spawn(new Location(world, bx, by, bz), BlockDisplay.class, viewer, e -> {
                e.setBlock(data);
                e.setBrightness(light);
                e.setViewRange(spec.viewRange());
                e.setInterpolationDelay(0);
                e.setInterpolationDuration(0);
                e.setTransformation(Transformations.of(track.spawn()));
            });
        } catch (RuntimeException e) {
            warn.warn("error:dropBlock", "Effects.dropBlock() could not spawn the display; landing instantly", e);
            return landInstantly(impact, onLanded);
        }
        UUID entityId = display.getUniqueId();
        entities.add(entityId);
        DropJob job = new DropJob(spec.track(), new BlockDropBody(display),
                () -> viewer.isOnline() && viewer.getWorld().equals(world),
                impact, () -> effects.safeRun(onLanded, "onLanded"),
                ended -> {
                    jobs.remove(id, ended);
                    entities.remove(entityId);
                });
        jobs.add(id, job);
        ticker.start(job);
        return true;
    }

    void cancelViewer(UUID viewer) {
        jobs.cancelViewer(viewer);
    }

    int total() {
        return jobs.total();
    }

    boolean tracks(UUID entity) {
        return entities.contains(entity);
    }

    private boolean landInstantly(Runnable impact, Runnable onLanded) {
        impact.run();
        effects.safeRun(onLanded, "onLanded");
        return false;
    }

    /** Impact of a drop: the block's own particles, the impact effect, then the caller's callback. */
    private void impact(LayerSpec.BlockDrop spec, Player viewer, World world, int bx, int by, int bz,
                        BlockData data, @Nullable Runnable onImpact) {
        try {
            if (spec.impactParticles() > 0 && !data.getMaterial().isAir()) {
                viewer.spawnParticle(Particle.BLOCK, bx + 0.5, by + 0.1, bz + 0.5, spec.impactParticles(),
                        0.3, 0.05, 0.3, 0, data);
            }
            if (spec.impactEffect() != null && effects.isLoaded()) {
                effects.play(spec.impactEffect(), new Location(world, bx + 0.5, by, bz + 0.5), viewer);
            }
        } catch (RuntimeException e) {
            warn.warn("error:impact", "Block drop impact effect failed and was skipped", e);
        }
        if (onImpact != null) {
            effects.safeRun(onImpact, "onImpact");
        }
    }

    /**
     * Fixed display light for a drop: the light just above the highest block of the target column (the roof once
     * the house stands), so the falling block isn't dark inside the real, server-side house.
     */
    private static Display.Brightness lightAbove(World world, int bx, int by, int bz) {
        int y = Math.max(world.getHighestBlockYAt(bx, bz), by) + 1;
        y = Math.min(y, world.getMaxHeight() - 1);
        Block above = world.getBlockAt(bx, y, bz);
        return new Display.Brightness(above.getLightFromBlocks(), above.getLightFromSky());
    }

    /** The {@code block-drop} layer of {@value Effects#BLOCK_DROP}, or the built-in defaults (logged once). */
    private LayerSpec.BlockDrop spec() {
        LayerSpec.BlockDrop spec = library.blockDrop(Effects.BLOCK_DROP);
        if (spec != null) {
            return spec;
        }
        if (effects.isLoaded()) {
            warn.warn("drop:" + Effects.BLOCK_DROP,
                    "Effect '" + Effects.BLOCK_DROP + "' has no block-drop layer; using the defaults");
        }
        return DEFAULT_DROP;
    }
}
