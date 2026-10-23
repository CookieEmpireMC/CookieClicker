package dev.casperrs.cookieClicker.house.cinematic;

import dev.casperrs.cookieClicker.config.HouseSettings;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotPresenceListener;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotTracker;
import dev.casperrs.cookieClicker.plot.Presence;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.vfx.Effects;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final CinematicExits exits;
    private final HouseRebuild rebuild;
    private final HouseShapes shapes;
    private final RevealSteps steps;
    private final RunPlanner planner;

    private final Map<UUID, CinematicRun> running = new HashMap<>();
    /** Players waiting for their {@code house_seen} answer, with the generation they asked in. */
    private final Map<UUID, Long> pending = new HashMap<>();
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
        this.exits = new CinematicExits(this);
        this.rebuild = new HouseRebuild(this, seen, tracker, messages);
        this.planner = new RunPlanner(fakeBlocks, settings);
        this.shapes = new HouseShapes(plugin.getLogger(), plots, settings);
        this.steps = new RevealSteps(plugin.getLogger(), fakeBlocks, effects, sounds, settings, this::complete,
                run -> abort(run.player, true));
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
            plugin.getServer().getPluginManager().registerEvents(exits, plugin);
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
        shapes.forget(plotId);
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
            HandlerList.unregisterAll(exits);
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
        HouseShapes.Shape shape = plot == null ? null : shapes.get(plotId);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        if (shape == null || world == null) {
            return Result.NO_HOUSE;
        }
        if (!player.getWorld().equals(world)) {
            return Result.WRONG_WORLD;
        }
        if (!RunPlanner.loaded(world, shape.box())) {
            return Result.NOT_LOADED;
        }
        RevealPlan plan = planner.plan(world, shape);
        if (plan == null) {
            return Result.NOTHING_TO_BUILD;
        }
        RunPlanner.Setup setup = planner.setup(player, plot, world, shape, plan);
        // checked where they will stand, before any teleport: inside hidden blocks, or on one (they'd fall)
        Location feet = setup.feet();
        if (HouseBox.bodyOverlaps(feet.getX(), feet.getY(), feet.getZ(), plan::isHidden)
                || HouseBox.bodyOverlaps(feet.getX(), feet.getY() - 0.5, feet.getZ(), plan::isHidden)) {
            return Result.INSIDE;
        }
        return begin(player, plotId, setup);
    }

    /** Camera or teleport, keep-out, then the first step (the house disappears this tick). */
    private Result begin(Player player, String plotId, RunPlanner.Setup setup) {
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
            steps.step(run, player);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "House cinematic of " + player.getName() + " failed; restoring", e);
            abort(id, true);
            return Result.NOTHING_TO_BUILD;
        }
        return Result.STARTED;
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
                steps.step(run, p);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "House cinematic of " + p.getName() + " failed; restoring", e);
                abort(run.player, true);
            }
        }
        if (running.isEmpty()) {
            stopTask();
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
    boolean abort(UUID id, boolean online, boolean goBack) {
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
    void resumeAmbienceLater(UUID id) {
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

    /** {@code /bakery house rebuild <player>}: forgets "seen" and plays the cinematic now if they are in the plot. */
    public void rebuild(CommandSender sender, String plotId, String name) {
        rebuild.rebuild(sender, plotId, name);
    }

    public boolean isRunning(UUID player) {
        return running.containsKey(player);
    }

    boolean isSelfTeleport(UUID player) {
        return selfTeleport.contains(player);
    }

    void clearKeepOut(Player player) {
        keepOut.clear(player);
    }

    /** The player left the server: their run ends without resending blocks, everything about them is forgotten. */
    void quit(UUID id) {
        pending.remove(id);
        abort(id, false);
        ambience.stop(id);
        selfTeleport.remove(id);
    }

    private static double square(double v) {
        return v * v;
    }
}
