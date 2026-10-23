package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.vfx.Effects;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One tick of a run: fake air for the next hidden blocks, the next blocks dropped through the effects engine, and
 * every landed block made real for its player with its sound and sparkle. Main thread only.
 */
final class RevealSteps {

    private final Logger log;
    private final FakeBlocks fakeBlocks;
    private final Effects effects;
    private final SoundService sounds;
    private final Supplier<HouseSettings> settings;
    private final BiConsumer<CinematicRun, Player> complete;
    private final Consumer<CinematicRun> fail;

    /**
     * @param complete called once every block landed
     * @param fail     aborts the run after a block could not be made real
     */
    RevealSteps(Logger log, FakeBlocks fakeBlocks, Effects effects, SoundService sounds,
                Supplier<HouseSettings> settings, BiConsumer<CinematicRun, Player> complete,
                Consumer<CinematicRun> fail) {
        this.log = log;
        this.fakeBlocks = fakeBlocks;
        this.effects = effects;
        this.sounds = sounds;
        this.settings = settings;
        this.complete = complete;
        this.fail = fail;
    }

    void step(CinematicRun run, Player p) {
        RevealPlan plan = run.plan;
        plan.beginTick();
        int[] n = {0};
        plan.nextHide(i -> run.sendBuffer[n[0]++] = plan.position(i));
        plan.nextResend(i -> run.sendBuffer[n[0]++] = plan.position(i));
        fakeBlocks.sendAir(p, run.world, run.sendBuffer, n[0]);
        int i;
        while (run.alive && (i = plan.nextDrop()) >= 0) {
            drop(run, p, i);
        }
        if (run.alive && plan.complete()) {
            complete.accept(run, p);
        }
    }

    /** Starts one block's drop. The plan already marked it falling, so callbacks inside this call are fine. */
    private void drop(CinematicRun run, Player p, int i) {
        long pos = run.plan.position(i);
        int x = RegionShape.unpackX(pos);
        int y = RegionShape.unpackY(pos);
        int z = RegionShape.unpackZ(pos);
        if (!run.world.isChunkLoaded(x >> 4, z >> 4)) {
            run.plan.landed(i); // the client lost that chunk anyway; it gets the real block with it
            return;
        }
        BlockData data = run.world.getBlockData(x, y, z);
        boolean bottom = run.plan.bottomLayer(i);
        Location foot = new Location(run.world, x + 0.5, y, z + 0.5);
        boolean animating = effects.dropBlock(p, data, new Location(run.world, x, y, z),
                () -> impact(run, p, foot, bottom),
                () -> landed(run, p, i));
        if (!animating && run.alive && run.plan.isFalling(i)) {
            landed(run, p, i); // the engine did nothing (no callbacks): reveal it ourselves
        }
    }

    private void impact(CinematicRun run, Player p, Location foot, boolean bottom) {
        if (!run.alive) {
            return;
        }
        sounds.play(p, HouseCinematics.SOUND_LAND, foot);
        int every = settings.get().cinematic().hammerEvery();
        if (every > 0 && run.impacts++ % every == 0) {
            sounds.play(p, HouseCinematics.SOUND_HAMMER, foot);
        }
        if (bottom) {
            effects.play(HouseCinematics.EFFECT_DUST, foot.clone(), p);
        }
    }

    /** The falling display is gone: make the block real for this player. Re-entrant and idempotent. */
    private void landed(CinematicRun run, Player p, int i) {
        if (!run.alive || !run.plan.landed(i)) {
            return;
        }
        long pos = run.plan.position(i);
        try {
            fakeBlocks.sendReal(p, run.world, new long[] {pos}, 1, run.tiles);
        } catch (RuntimeException e) { // the plan already counts it real: restore everything instead
            log.log(Level.SEVERE, "House cinematic of " + p.getName() + " failed; restoring", e);
            try {
                fakeBlocks.sendReal(p, run.world, new long[] {pos}, 1, run.tiles);
            } catch (RuntimeException again) {
                log.log(Level.SEVERE, "House cinematic abort: restore landed block failed", again);
            }
            fail.accept(run);
            return;
        }
        int x = RegionShape.unpackX(pos);
        int y = RegionShape.unpackY(pos);
        int z = RegionShape.unpackZ(pos);
        Location foot = new Location(run.world, x + 0.5, y, z + 0.5);
        sounds.playBlock(p, HouseCinematics.SOUND_PLACE, foot, run.world.getBlockData(x, y, z));
        effects.play(HouseCinematics.EFFECT_SPARKLE, foot.clone(), p);
        if (run.plan.complete()) {
            complete.accept(run, p);
        }
    }
}
