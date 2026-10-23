package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotPresenceListener;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotTracker;
import dev.casperrs.cookieClicker.plot.Presence;
import dev.casperrs.cookieClicker.plot.RegionShape;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.vfx.Effects;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The first-visit build cinematic: the player gets fake air over their plot's house, then the real blocks drop in
 * layer by layer ({@link RevealPlan}, {@link Effects#dropBlock}), only for them; the world is never changed. Also
 * starts and stops the idle {@link PlotAmbience} around a run.
 *
 * <p>Every exit (quit, death, teleport, world change, leaving the plot, reload, disable, a failing tick) goes through
 * {@link #abort}: effects cancelled first, every still-hidden block sent real again, keep-out and camera ended, and
 * the house not marked seen. "Seen" is stored only on completion. Main thread only; one cinematic per player.
 */
public final class HouseCinematics implements Listener, PlotPresenceListener {

    public static final String SOUND_LAND = "house_block_land";
    public static final String SOUND_HAMMER = "house_hammer";
    public static final String SOUND_PLACE = "house_block_place";
    public static final String SOUND_FANFARE = "house_fanfare";
    public static final String EFFECT_DUST = "house_foundation_dust";
    public static final String EFFECT_SPARKLE = "house_sparkle";
    public static final String EFFECT_CONFETTI = "house_confetti";

    /** Why {@link #start} did or didn't start. */
    public enum Result { STARTED, NO_HOUSE, WRONG_WORLD, NOT_LOADED, INSIDE, NOTHING_TO_BUILD }

    /** A house region's geometry, computed once per plot and reload. */
    private record Shape(HouseBox box, long[] blocks) {
    }

    /** Where a run starts: the blocks to reveal and where the player watches from. */
    private record Setup(World world, HouseBox box, RevealPlan plan, Set<Long> tiles, Location feet,
                         @Nullable Location camera, boolean teleport) {
    }

    private final Plugin plugin;
    private final PlotRegistry plots;
    private final PlotTracker tracker;
    private final HouseSeen seen;
    private final Effects effects;
    private final SoundService sounds;
    private final Messages messages;
    private final Supplier<HouseSettings> settings;
    private final FakeBlocks fakeBlocks = new FakeBlocks();
    private final CinematicKeepOut keepOut;
    private final PlotAmbience ambience;
    private final CinematicCamera camera;
    private final CinematicGuards guards;
    private final HouseRebuild rebuild;

    private final Map<UUID, CinematicRun> running = new HashMap<>();
    /** Players waiting for their {@code house_seen} answer, with the generation they asked in. */
    private final Map<UUID, Long> pending = new HashMap<>();
    private final Map<String, Optional<Shape>> shapes = new HashMap<>();
    /** Players we are teleporting ourselves right now (our own teleport must not abort anything). */
    private final Set<UUID> selfTeleport = new HashSet<>();
    private long generation;
    private @Nullable BukkitTask task;
    private boolean registered;

    public HouseCinematics(Plugin plugin, PlotRegistry plots, PlotTracker tracker, HouseSeen seen, Effects effects,
                           SoundService sounds, Messages messages, Supplier<HouseSettings> settings) {
        this.plugin = plugin;
        this.plots = plots;
        this.tracker = tracker;
        this.seen = seen;
        this.effects = effects;
        this.sounds = sounds;
        this.messages = messages;
        this.settings = settings;
        this.keepOut = new CinematicKeepOut(plugin);
        this.ambience = new PlotAmbience(plugin, effects, sounds, settings);
        this.camera = new CinematicCamera(plugin, selfTeleport, this::cameraLost);
        this.guards = new CinematicGuards(running, selfTeleport, settings);
        this.rebuild = new HouseRebuild(this, seen, tracker, messages);
    }

    /** The camera was lost from outside (another game mode, entity gone): end the run, or the camera on its own. */
    private void cameraLost(UUID id) {
        if (abort(id, true)) {
            resumeAmbienceLater(id);
            return;
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            guarded("end camera", () -> camera.exit(p, true, true));
        }
    }

    /** Enable: listen to events and plot presence. Call before {@link PlotTracker#start()}. */
    public void start() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getServer().getPluginManager().registerEvents(camera, plugin);
            plugin.getServer().getPluginManager().registerEvents(guards, plugin);
            tracker.addListener(this);
            registered = true;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            keepOut.clear(p); // left over from a plugin reload
        }
    }

    /** {@code /bakery reload}, before effects and plots reload: ends every cinematic (restoring the blocks). */
    public void abortAll() {
        generation++;
        pending.clear();
        for (UUID id : new ArrayList<>(running.keySet())) {
            abort(id, true);
        }
        ambience.stopAll();
        shapes.clear();
    }

    /** {@code /bakery reload}, after effects, plots and the tracker: ambience again for everyone in a plot. */
    public void restart() {
        shapes.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String plot = tracker.presence(p.getUniqueId()).plot();
            if (plot != null) {
                startAmbience(p, plot);
            }
        }
    }

    /** A plot setup (or undo) changed a plot's house box or points: forget its shape, restart its ambience. */
    public void plotChanged(String plotId) {
        shapes.remove(plotId);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plotId.equals(tracker.presence(p.getUniqueId()).plot()) && !running.containsKey(p.getUniqueId())) {
                ambience.stop(p.getUniqueId());
                startAmbience(p, plotId);
            }
        }
    }

    public void shutdown() {
        abortAll();
        camera.exitAll();
        stopTask();
        if (registered) {
            HandlerList.unregisterAll(this);
            HandlerList.unregisterAll(camera);
            HandlerList.unregisterAll(guards);
            tracker.removeListener(this);
            registered = false;
        }
    }

    @Override
    public void onEnterPlot(Player player, String plotId) {
        HouseSettings.Cinematic c = settings.get().cinematic();
        if (c.enabled() && c.trigger() == HouseSettings.Trigger.ENTER_PLOT
                && !selfTeleport.contains(player.getUniqueId())) { // back from our own camera: no new run
            request(player, plotId);
        } else {
            startAmbience(player, plotId);
        }
    }

    @Override
    public void onLeavePlot(Player player, String plotId) {
        UUID id = player.getUniqueId();
        pending.remove(id);
        CinematicRun run = running.get(id);
        if (run != null && run.plotId.equals(plotId) && !run.camera) { // the camera point may lie outside the plot
            abort(id, true);
        }
        ambience.stop(id);
    }

    /** Asks storage whether the player has seen this plot's house, then starts the cinematic or the ambience. */
    private void request(Player player, String plotId) {
        UUID id = player.getUniqueId();
        if (running.containsKey(id) || pending.containsKey(id)) {
            return;
        }
        long gen = generation;
        pending.put(id, gen);
        seen.isSeen(id, plotId, answer -> {
            Long asked = pending.get(id);
            if (asked == null || asked != gen || gen != generation) {
                return; // left, quit or reloaded meanwhile
            }
            pending.remove(id);
            Player p = Bukkit.getPlayer(id);
            Presence where = tracker.presence(id);
            if (p == null || !where.inPlot(plotId) || running.containsKey(id)) {
                return;
            }
            HouseSettings.Cinematic c = settings.get().cinematic();
            boolean blocked = c.trigger() == HouseSettings.Trigger.ENTER_PLOT && where.inHouse(plotId)
                    && !c.teleportToViewpoint();
            if (answer || blocked || !c.enabled() || start(p, plotId) != Result.STARTED) {
                startAmbience(p, plotId);
            }
        });
    }

    /** {@code trigger: viewpoint}: a teleport that ends at a plot's viewpoint starts the cinematic (a tick later). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportToViewpoint(PlayerTeleportEvent event) {
        HouseSettings.Cinematic c = settings.get().cinematic();
        UUID id = event.getPlayer().getUniqueId();
        if (!c.enabled() || c.trigger() != HouseSettings.Trigger.VIEWPOINT || selfTeleport.contains(id)
                || running.containsKey(id)) {
            return;
        }
        long gen = generation;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            String plotId = p == null || gen != generation ? null : tracker.presence(id).plot();
            if (plotId == null) {
                return;
            }
            Plot.Viewpoint v = plots.get(plotId).map(Plot::viewpoint).orElse(null);
            Location at = p.getLocation();
            if (v != null && square(at.getX() - v.x()) + square(at.getY() - v.y()) + square(at.getZ() - v.z())
                    <= square(c.viewpointRadius())) {
                ambience.stop(id);
                request(p, plotId);
            }
        });
    }

    /**
     * Starts the cinematic for {@code player} at {@code plotId} now, without checking "seen" (also used by
     * {@code /bakery house rebuild}). A running one is aborted first.
     */
    public Result start(Player player, String plotId) {
        forget(player.getUniqueId());
        Plot plot = plots.get(plotId).orElse(null);
        Shape shape = plot == null ? null : shape(plotId);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        if (shape == null || world == null) {
            return Result.NO_HOUSE;
        }
        if (!player.getWorld().equals(world)) {
            return Result.WRONG_WORLD;
        }
        if (!loaded(world, shape.box())) {
            return Result.NOT_LOADED;
        }
        RevealPlan plan = plan(world, shape);
        if (plan == null) {
            return Result.NOTHING_TO_BUILD;
        }
        Setup setup = setup(player, plot, world, shape, plan);
        // checked where they will stand, before any teleport: inside hidden blocks, or on one (they'd fall)
        Location feet = setup.feet();
        if (HouseBox.bodyOverlaps(feet.getX(), feet.getY(), feet.getZ(), plan::isHidden)
                || HouseBox.bodyOverlaps(feet.getX(), feet.getY() - 0.5, feet.getZ(), plan::isHidden)) {
            return Result.INSIDE;
        }
        return begin(player, plotId, setup);
    }

    private static boolean loaded(World world, HouseBox box) {
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
    private @Nullable RevealPlan plan(World world, Shape shape) {
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

    private Setup setup(Player player, Plot plot, World world, Shape shape, RevealPlan plan) {
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

    /** Camera or teleport, keep-out, then the first step (the house disappears this tick). */
    private Result begin(Player player, String plotId, Setup setup) {
        UUID id = player.getUniqueId();
        boolean filmed = setup.camera() != null && camera.enter(player, setup.camera(), setup.feet());
        if (setup.teleport() && !filmed) {
            selfTeleport.add(id);
            try {
                player.teleport(setup.feet());
            } finally {
                selfTeleport.remove(id);
            }
            if (!player.isOnline() || !player.getWorld().equals(setup.world())) {
                return Result.WRONG_WORLD;
            }
        }
        try {
            ambience.stop(id);
            if (!filmed) {
                HouseSettings.Cinematic c = settings.get().cinematic();
                keepOut.apply(player, c.keepOut(), c.slowAmount());
            }
        } catch (RuntimeException e) { // no run yet: nothing else would end the camera or the keep-out
            plugin.getLogger().log(Level.SEVERE, "House cinematic of " + player.getName() + " failed to start", e);
            guarded("clear keep-out", () -> keepOut.clear(player));
            if (filmed) {
                guarded("end camera", () -> camera.exit(player, true, true));
            }
            return Result.NOTHING_TO_BUILD;
        }
        CinematicRun run = new CinematicRun(id, plotId, setup.world(), setup.box(), setup.plan(), setup.tiles(),
                filmed);
        running.put(id, run);
        ensureTask();
        try {
            step(run, player);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "House cinematic of " + player.getName() + " failed; restoring", e);
            abort(id, true);
            return Result.NOTHING_TO_BUILD;
        }
        return Result.STARTED;
    }

    private @Nullable Shape shape(String plotId) {
        return shapes.computeIfAbsent(plotId, k -> plots.houseShape(k).flatMap(s -> {
            long max = settings.get().maxBlocks();
            if (s.boxVolume() > max) {
                plugin.getLogger().warning("House region of plot " + k + " is " + s.boxVolume()
                        + " blocks, more than house.max-blocks (" + max + "); no build cinematic there");
                return Optional.empty();
            }
            return Optional.of(new Shape(HouseBox.of(s), s.blocks()));
        })).orElse(null);
    }

    private void ensureTask() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (CinematicRun run : new ArrayList<>(running.values())) {
            if (!run.alive) {
                continue;
            }
            Player p = Bukkit.getPlayer(run.player);
            if (p == null) {
                abort(run.player, false);
                continue;
            }
            try {
                step(run, p);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "House cinematic of " + p.getName() + " failed; restoring", e);
                abort(run.player, true);
            }
        }
        if (running.isEmpty()) {
            stopTask();
        }
    }

    private void step(CinematicRun run, Player p) {
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
            complete(run, p);
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
        sounds.play(p, SOUND_LAND, foot);
        int every = settings.get().cinematic().hammerEvery();
        if (every > 0 && run.impacts++ % every == 0) {
            sounds.play(p, SOUND_HAMMER, foot);
        }
        if (bottom) {
            effects.play(EFFECT_DUST, foot.clone(), p);
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
            plugin.getLogger().log(Level.SEVERE, "House cinematic of " + p.getName() + " failed; restoring", e);
            guarded("restore landed block", () -> fakeBlocks.sendReal(p, run.world, new long[] {pos}, 1, run.tiles));
            abort(run.player, true);
            return;
        }
        int x = RegionShape.unpackX(pos);
        int y = RegionShape.unpackY(pos);
        int z = RegionShape.unpackZ(pos);
        Location foot = new Location(run.world, x + 0.5, y, z + 0.5);
        sounds.playBlock(p, SOUND_PLACE, foot, run.world.getBlockData(x, y, z));
        effects.play(EFFECT_SPARKLE, foot.clone(), p);
        if (run.plan.complete()) {
            complete(run, p);
        }
    }

    private void complete(CinematicRun run, Player p) {
        if (!run.alive) {
            return;
        }
        run.alive = false;
        running.remove(run.player, run);
        keepOut.clear(p);
        seen.markSeen(run.player, run.plotId); // before the camera's teleport back into the plot
        if (run.camera) {
            guarded("end camera", () -> camera.exit(p, true, true));
        }
        HouseBox box = run.box;
        Location top = new Location(run.world, box.centreX(), box.maxY() + settings.get().cinematic().confettiHeight(),
                box.centreZ());
        effects.play(EFFECT_CONFETTI, top, p);
        sounds.play(p, SOUND_FANFARE);
        p.showTitle(Title.title(messages.component("house.cinematic.title"),
                messages.component("house.cinematic.subtitle")));
        if (tracker.presence(run.player).inPlot(run.plotId)) {
            startAmbience(p, run.plotId);
        }
    }

    /**
     * Ends the player's cinematic without marking it seen: falling blocks cancelled (no callbacks), every
     * still-hidden block sent real again right now if {@code online}, keep-out removed.
     *
     * @return whether a cinematic was running
     */
    public boolean abort(UUID id, boolean online) {
        return abort(id, online, online);
    }

    /** @param goBack a camera run puts the player back at the viewpoint (false: they are being teleported away) */
    private boolean abort(UUID id, boolean online, boolean goBack) {
        CinematicRun run = running.remove(id);
        if (run == null) {
            return false;
        }
        run.alive = false;
        long[] hidden = run.plan.abort();
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            // each step on its own: a throwing cancelFor must never leave a missing house or a slowed player
            guarded("cancel effects", () -> effects.cancelFor(p));
            if (online) {
                guarded("restore blocks", () -> fakeBlocks.sendReal(p, run.world, hidden, hidden.length, run.tiles));
            }
            guarded("clear keep-out", () -> keepOut.clear(p));
            if (run.camera) {
                guarded("end camera", () -> camera.exit(p, online, goBack));
            }
        }
        return true;
    }

    /** Aborts a running cinematic and drops a pending "seen" question. */
    void forget(UUID id) {
        abort(id, true);
        pending.remove(id);
    }

    private void guarded(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "House cinematic abort: " + what + " failed", e);
        }
    }

    /** After an abort that leaves the player in the plot (death, teleport): ambience again a tick later. */
    private void resumeAmbienceLater(UUID id) {
        long gen = generation;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            String plot = p == null || gen != generation || running.containsKey(id) ? null
                    : tracker.presence(id).plot();
            if (plot != null) {
                startAmbience(p, plot);
            }
        });
    }

    /** Starts the plot's idle smoke, steam and hum for the player, unless they are mid-cinematic. */
    void startAmbience(Player p, String plotId) {
        if (running.containsKey(p.getUniqueId())) {
            return;
        }
        plots.get(plotId).ifPresent(plot -> plots.world(plot).ifPresent(w -> ambience.start(p, plot, w,
                plots.houseShape(plotId).map(h -> new Location(w, (h.minX() + h.maxX() + 1) / 2.0,
                        (h.minY() + h.maxY() + 1) / 2.0, (h.minZ() + h.maxZ() + 1) / 2.0)).orElse(null))));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        keepOut.clear(event.getPlayer()); // transient modifiers never survive a quit, but be sure
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pending.remove(id);
        abort(id, false);
        ambience.stop(id);
        selfTeleport.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (abort(id, true)) {
            resumeAmbienceLater(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        abort(event.getPlayer().getUniqueId(), true); // nothing is sent to the new world; the client dropped them
    }

    /** Any real teleport ends the cinematic (tiny server position corrections don't). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!running.containsKey(id) || selfTeleport.contains(id)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != null && from.getWorld().equals(to.getWorld()) && from.distanceSquared(to) < 1.0) {
            return;
        }
        abort(id, true, false); // they go where this teleport takes them, in their own game mode
        resumeAmbienceLater(id);
    }

    /** {@code /bakery house rebuild <player>}: forgets "seen" and plays the cinematic now if they are in the plot. */
    public void rebuild(CommandSender sender, String plotId, String name) {
        rebuild.rebuild(sender, plotId, name);
    }

    public boolean isRunning(UUID player) {
        return running.containsKey(player);
    }

    private static double square(double v) {
        return v * v;
    }
}
