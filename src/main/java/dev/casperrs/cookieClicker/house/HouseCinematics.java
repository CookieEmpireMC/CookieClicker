package dev.casperrs.cookieClicker.house;

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
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
 * The first-visit build cinematic (starter-house): the player gets fake air over their plot's house, then the real
 * blocks drop in layer by layer ({@link RevealPlan}, {@link Effects#dropBlock}), only for them. The house is never
 * changed server-side. Also owns the chimney smoke lifecycle ({@link ChimneySmoke}).
 *
 * <p>Safety: quit, death, teleport, world change, leaving the plot, {@code /bakery reload} and disable all go through
 * {@link #abort}: {@code effects.cancelFor(player)} first, then every still-hidden block is sent real again right
 * away (when the player is still online), the keep-out modifiers are removed, and the house is not marked seen.
 * "Seen" is stored only on completion. Main thread only; one cinematic per player.
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

    /** A house region's cached geometry ({@link RegionShape#blocks()} is computed once per plot and reload). */
    private record Shape(HouseBox box, long[] blocks) {
    }

    /** One running cinematic. */
    private static final class Run {
        final UUID player;
        final String plotId;
        final World world;
        final HouseBox box;
        final RevealPlan plan;
        final Set<Long> tiles;
        final long[] sendBuffer;
        int impacts;
        boolean alive = true;

        Run(UUID player, String plotId, World world, HouseBox box, RevealPlan plan, Set<Long> tiles) {
            this.player = player;
            this.plotId = plotId;
            this.world = world;
            this.box = box;
            this.plan = plan;
            this.tiles = tiles;
            this.sendBuffer = new long[2 * plan.size()]; // hide + re-assert in one tick
        }
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
    private final KeepOut keepOut;
    private final ChimneySmoke smoke;

    private final Map<UUID, Run> running = new HashMap<>();
    /** Players waiting for their {@code house_seen} answer, with the generation they asked in. */
    private final Map<UUID, Long> pending = new HashMap<>();
    private final Map<String, Optional<Shape>> shapes = new HashMap<>();
    /** Players we are teleporting ourselves right now (our own teleport must not abort anything). */
    private final Set<UUID> selfTeleport = new HashSet<>();
    private long generation;
    private BukkitTask task;
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
        this.keepOut = new KeepOut(plugin);
        this.smoke = new ChimneySmoke(effects, sounds, () -> settings.get().chimney());
    }

    /** Enable: listen to events and plot presence. Call before {@link PlotTracker#start()}. */
    public void start() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
        smoke.stopAll();
        shapes.clear();
    }

    /** {@code /bakery reload}, after effects, plots and the tracker: smoke again for everyone in a plot. */
    public void restart() {
        shapes.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String plot = tracker.presence(p.getUniqueId()).plot();
            if (plot != null) {
                startSmoke(p, plot);
            }
        }
    }

    /** Disable: everything off, every house restored. */
    public void shutdown() {
        abortAll();
        stopTask();
        if (registered) {
            HandlerList.unregisterAll(this);
            tracker.removeListener(this);
            registered = false;
        }
    }

    // ---- triggers ----------------------------------------------------------------------------------------------

    @Override
    public void onEnterPlot(Player player, String plotId) {
        HouseSettings.Cinematic c = settings.get().cinematic();
        if (c.enabled() && c.trigger() == HouseSettings.Trigger.ENTER_PLOT) {
            request(player, plotId);
        } else {
            startSmoke(player, plotId);
        }
    }

    @Override
    public void onLeavePlot(Player player, String plotId) {
        UUID id = player.getUniqueId();
        pending.remove(id);
        Run run = running.get(id);
        if (run != null && run.plotId.equals(plotId)) {
            abort(id, true);
        }
        smoke.stop(id);
    }

    /** Asks storage whether the player has seen this plot's house, then starts the cinematic or the smoke. */
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
                startSmoke(p, plotId);
            }
        });
    }

    /** {@code trigger: viewpoint}: a teleport that ends at a plot's viewpoint starts the cinematic (checked a tick later). */
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
            Plot plot = plots.get(plotId).orElse(null);
            Plot.Viewpoint v = plot == null ? null : plot.viewpoint();
            Location at = p.getLocation();
            if (v != null && square(at.getX() - v.x()) + square(at.getY() - v.y()) + square(at.getZ() - v.z())
                    <= square(c.viewpointRadius())) {
                smoke.stop(id);
                request(p, plotId);
            }
        });
    }

    // ---- start -------------------------------------------------------------------------------------------------

    /**
     * Starts the cinematic for {@code player} at {@code plotId} now, without checking "seen" (also used by
     * {@code /bakery house rebuild}). A running one is aborted first.
     */
    public Result start(Player player, String plotId) {
        UUID id = player.getUniqueId();
        abort(id, true);
        pending.remove(id);
        Plot plot = plots.get(plotId).orElse(null);
        Shape shape = plot == null ? null : shape(plotId);
        World world = plot == null ? null : plots.world(plot).orElse(null);
        if (shape == null || world == null) {
            return Result.NO_HOUSE;
        }
        HouseBox box = shape.box();
        if (!player.getWorld().equals(world)) {
            return Result.WRONG_WORLD;
        }
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return Result.NOT_LOADED;
                }
            }
        }
        HouseSettings.Cinematic c = settings.get().cinematic();
        if (c.teleportToViewpoint() && plot.viewpoint() != null) {
            Plot.Viewpoint v = plot.viewpoint();
            selfTeleport.add(id);
            try {
                player.teleport(new Location(world, v.x(), v.y(), v.z(), v.yaw(), v.pitch()));
            } finally {
                selfTeleport.remove(id);
            }
            if (!player.isOnline() || !player.getWorld().equals(world)) {
                return Result.WRONG_WORLD;
            }
        }

        // only blocks that are really there (air stays air), real data is read again when each one lands
        long[] blocks = new long[shape.blocks().length];
        int n = 0;
        Set<Long> tiles = new HashSet<>();
        for (long p : shape.blocks()) {
            int x = RegionShape.unpackX(p);
            int y = RegionShape.unpackY(p);
            int z = RegionShape.unpackZ(p);
            if (!world.getType(x, y, z).isAir()) {
                blocks[n++] = p;
                if (fakeBlocks.isTile(world, x, y, z)) {
                    tiles.add(p);
                }
            }
        }
        if (n == 0) {
            return Result.NOTHING_TO_BUILD;
        }
        RevealPlan plan = new RevealPlan(RevealOrder.of(Arrays.copyOf(blocks, n)), new RevealPlan.Pace(
                c.blocksPerTick(), c.maxFallingDisplays(), c.layerDelayTicks(), c.fakeAirPerTick(),
                c.refreshHiddenTicks()));
        Location feet = player.getLocation();
        if (HouseBox.bodyOverlaps(feet.getX(), feet.getY(), feet.getZ(), plan::isHidden)) {
            return Result.INSIDE; // they'd stand inside invisible solid blocks
        }

        smoke.stop(id);
        keepOut.apply(player, c.keepOut(), c.slowAmount());
        Run run = new Run(id, plotId, world, box, plan, tiles);
        running.put(id, run);
        ensureTask();
        step(run, player); // the house disappears this tick
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

    // ---- per tick ----------------------------------------------------------------------------------------------

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
        for (Run run : new ArrayList<>(running.values())) {
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

    private void step(Run run, Player p) {
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
    private void drop(Run run, Player p, int i) {
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

    private void impact(Run run, Player p, Location foot, boolean bottom) {
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

    /** onLanded: the display is gone; make the block real for this player. Re-entrant and idempotent. */
    private void landed(Run run, Player p, int i) {
        if (!run.alive || !run.plan.landed(i)) {
            return;
        }
        long pos = run.plan.position(i);
        fakeBlocks.sendReal(p, run.world, new long[] {pos}, 1, run.tiles);
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

    private void complete(Run run, Player p) {
        if (!run.alive) {
            return;
        }
        run.alive = false;
        running.remove(run.player, run);
        keepOut.clear(p);
        HouseBox box = run.box;
        Location top = new Location(run.world, box.centreX(), box.maxY() + settings.get().cinematic().confettiHeight(),
                box.centreZ());
        effects.play(EFFECT_CONFETTI, top, p);
        sounds.play(p, SOUND_FANFARE);
        p.showTitle(Title.title(messages.component("house.cinematic.title"),
                messages.component("house.cinematic.subtitle")));
        seen.markSeen(run.player, run.plotId);
        if (tracker.presence(run.player).inPlot(run.plotId)) {
            startSmoke(p, run.plotId);
        }
    }

    // ---- abort / safety ----------------------------------------------------------------------------------------

    /**
     * Ends the player's cinematic without marking it seen: falling blocks and loops cancelled (no callbacks), every
     * still-hidden block sent real again right now if {@code online}, keep-out removed.
     *
     * @return whether a cinematic was running
     */
    public boolean abort(UUID id, boolean online) {
        Run run = running.remove(id);
        if (run == null) {
            return false;
        }
        run.alive = false;
        long[] hidden = run.plan.abort();
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            effects.cancelFor(p);
            if (online) {
                fakeBlocks.sendReal(p, run.world, hidden, hidden.length, run.tiles);
            }
            keepOut.clear(p);
        }
        return true;
    }

    /** After an abort that leaves the player in the plot (death, teleport): smoke again a tick later. */
    private void resumeSmokeLater(UUID id) {
        long gen = generation;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            String plot = p == null || gen != generation || running.containsKey(id) ? null
                    : tracker.presence(id).plot();
            if (plot != null) {
                startSmoke(p, plot);
            }
        });
    }

    private void startSmoke(Player p, String plotId) {
        if (running.containsKey(p.getUniqueId())) {
            return; // mid-cinematic: smoke comes at the end
        }
        plots.get(plotId).ifPresent(plot -> plots.world(plot).ifPresent(w -> smoke.start(p, plot, w)));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        keepOut.clear(event.getPlayer()); // stale modifiers (transient ones never survive, but be sure)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pending.remove(id);
        abort(id, false);
        smoke.stop(id);
        selfTeleport.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (abort(id, true)) {
            resumeSmokeLater(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        abort(event.getPlayer().getUniqueId(), true); // nothing is sent to the new world; the client dropped them
    }

    /** Keep-out guard: no teleport into a still-hidden block. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleportGuard(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Run run = running.get(id);
        Location to = event.getTo();
        if (run != null && !selfTeleport.contains(id) && run.world.equals(to.getWorld())
                && HouseBox.bodyOverlaps(to.getX(), to.getY(), to.getZ(), run.plan::isHidden)) {
            event.setCancelled(true);
        }
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
        abort(id, true);
        resumeSmokeLater(id);
    }

    /** Keep-out guard: no step into a still-hidden block (the client thinks it's air; the server would rubber-band). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (running.isEmpty()) {
            return;
        }
        Run run = running.get(event.getPlayer().getUniqueId());
        if (run == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return; // only looking around
        }
        if (HouseBox.bodyOverlaps(to.getX(), to.getY(), to.getZ(), run.plan::isHidden)
                && !HouseBox.bodyOverlaps(from.getX(), from.getY(), from.getZ(), run.plan::isHidden)) {
            event.setCancelled(true);
        }
    }

    // ---- re-assert fake air (the client loses it on interaction rollbacks and chunk resends) -------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Block b = event.getClickedBlock();
        if (b != null) {
            reassertNear(event.getPlayer(), b.getWorld(), b.getX(), b.getY(), b.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Location l = event.getRightClicked().getLocation();
        reassertNear(event.getPlayer(), l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        reassertNear(event.getPlayer(), b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Block b = event.getBlockPlaced();
        reassertNear(event.getPlayer(), b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkSent(PlayerChunkLoadEvent event) {
        Run run = running.get(event.getPlayer().getUniqueId());
        if (run != null && run.world.equals(event.getWorld())
                && run.box.overlapsChunk(event.getChunk().getX(), event.getChunk().getZ())) {
            run.plan.requestResend(); // sent on the next tick, after the chunk
        }
    }

    private void reassertNear(Player player, World world, int x, int y, int z) {
        Run run = running.get(player.getUniqueId());
        if (run != null && run.world.equals(world)
                && run.box.near(x, y, z, settings.get().cinematic().reassertRadius())) {
            run.plan.requestResend(); // next tick: after the server's rollback packets
        }
    }

    /** Whether the player is watching their cinematic right now. */
    public boolean isRunning(UUID player) {
        return running.containsKey(player);
    }

    private static double square(double v) {
        return v * v;
    }
}
