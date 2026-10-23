package dev.casperrs.cookieClicker.vfx;

import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.vfx.config.EffectsConfigParser;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The replayed presets ({@link Effects#loop}): one {@link LoopJob} per loop, capped per player. */
final class Loops {

    private final Effects effects;
    private final EffectTicker ticker;
    private final PresetLibrary library;
    private final WarnOnce warn;
    private final ViewerJobs<LoopJob> jobs = new ViewerJobs<>();

    Loops(Effects effects, EffectTicker ticker, PresetLibrary library, WarnOnce warn) {
        this.effects = effects;
        this.ticker = ticker;
        this.library = library;
        this.warn = warn;
    }

    EffectHandle start(String effectId, Location loc, Player viewer, int startDelay, @Nullable Runnable onRepeat) {
        if (!effects.ready("loop", effectId)) {
            return EffectHandle.NONE;
        }
        PresetLibrary.Compiled compiled = library.get(effectId);
        if (compiled == null) {
            warn.warn("unknown:" + effectId, "Unknown effect ID '" + effectId + "' (not in effects.yml); skipped");
            return EffectHandle.NONE;
        }
        World world = loc.getWorld();
        if (world == null || !viewer.isOnline() || !viewer.getWorld().equals(world)) {
            return EffectHandle.NONE;
        }
        UUID id = viewer.getUniqueId();
        int maxLoops = effects.limits().maxLoopsPerPlayer();
        if (!jobs.hasRoom(id, maxLoops, Integer.MAX_VALUE)) {
            warn.warn("loop-cap:" + effectId, "A player reached limits.max-loops-per-player (" + maxLoops
                    + "); loop of '" + effectId + "' not started");
            return EffectHandle.NONE;
        }
        int every = compiled.preset().loopEvery();
        if (every <= 0) {
            warn.warn("loop-every:" + effectId, "Effect '" + effectId + "' has no loop-every; looping every "
                    + EffectsConfigParser.DEFAULT_LOOP_EVERY + " ticks");
            every = EffectsConfigParser.DEFAULT_LOOP_EVERY;
        }
        Location at = loc.clone();
        int chunkX = at.getBlockX() >> 4;
        int chunkZ = at.getBlockZ() >> 4;
        LoopJob job = new LoopJob(every, startDelay,
                () -> viewer.isOnline() && viewer.getWorld().equals(world),
                () -> {
                    if (world.isChunkLoaded(chunkX, chunkZ)) {
                        effects.playLayers(compiled, at, viewer, Map.of());
                        if (onRepeat != null) {
                            effects.safeRun(onRepeat, "loop onRepeat");
                        }
                    }
                },
                ended -> jobs.remove(id, ended));
        jobs.add(id, job);
        ticker.start(job);
        return job;
    }

    void cancelViewer(UUID viewer) {
        jobs.cancelViewer(viewer);
    }

    int total() {
        return jobs.total();
    }
}
