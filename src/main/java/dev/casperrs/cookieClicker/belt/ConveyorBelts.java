package dev.casperrs.cookieClicker.belt;

import dev.casperrs.cookieClicker.config.BeltSettings;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotTracker;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.vfx.Effects;
import dev.casperrs.cookieClicker.view.StationViews;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The conveyor belts. Per plot with a belt one shared moving surface ({@link BeltSurface}); per player in a plot (and
 * not in their build cinematic) their own cookies ride it, shown only to them, at a speed and interval from their
 * CPS ({@link BeltCurve}, {@link BeltLane}). Each cookie is one interpolated transformation from the hatch to the
 * pedestal (no per-tick teleports); on arrival it is removed and {@code belt_arrive} plays. While a belt runs,
 * {@code belt_loop} plays on a per-player clock that keeps its beat across restarts. Purely visual. Main thread.
 *
 * <p>Cost: who rides, their CPS and the surfaces are refreshed every {@value #REFRESH_TICKS} ticks; the 1-tick ride
 * task only runs while someone rides. Entities are non-persistent and tagged {@value StationViews#TAG}.
 */
public final class ConveyorBelts implements Listener {

    public static final String SOUND_LOOP = "belt_loop";
    public static final String EFFECT_ARRIVE = "belt_arrive";
    static final int REFRESH_TICKS = 10;

    private final Plugin plugin;
    private final PlotRegistry plots;
    private final PlotTracker tracker;
    private final SessionManager sessions;
    private final Economy economy;
    private final Effects effects;
    private final SoundService sounds;
    private final Predicate<Player> hasPack;
    private final Predicate<UUID> inCinematic;
    private final Supplier<BeltSettings> settings;
    private final DoubleSupplier hearingRadius;
    private final WarnOnce warnings;

    private final Map<String, BeltSurface> surfaces = new HashMap<>();
    private final Map<UUID, BeltRider> riders = new HashMap<>();
    /** Pack status per online player, refilled each refresh. */
    private final Map<UUID, Boolean> packs = new HashMap<>();
    private final List<Player> online = new ArrayList<>();
    private @Nullable BukkitTask refreshTask;
    private @Nullable BukkitTask rideTask;
    private boolean registered;
    private long tick;

    /**
     * @param inCinematic   true while the player watches their build cinematic (no belt for them then)
     * @param hearingRadius {@code belt_loop} is heard within this many blocks
     */
    public ConveyorBelts(Plugin plugin, PlotRegistry plots, PlotTracker tracker, SessionManager sessions,
                         Economy economy, Effects effects, SoundService sounds, Predicate<Player> hasPack,
                         Predicate<UUID> inCinematic, Supplier<BeltSettings> settings, DoubleSupplier hearingRadius) {
        this.plugin = plugin;
        this.plots = plots;
        this.tracker = tracker;
        this.sessions = sessions;
        this.economy = economy;
        this.effects = effects;
        this.sounds = sounds;
        this.hasPack = hasPack;
        this.inCinematic = inCinematic;
        this.settings = settings;
        this.hearingRadius = hearingRadius;
        this.warnings = new WarnOnce(plugin.getLogger());
    }

    public void start() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        if (refreshTask == null) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSafely, 1, REFRESH_TICKS);
        }
    }

    /** {@code /bakery reload}: everything goes; the next refresh rebuilds it with the new plots and settings. */
    public void restart() {
        removeAll();
        warnings.clear();
    }

    /** A plot setup (or undo) changed a plot: its surface and riders are rebuilt on the next refresh. */
    public void plotChanged(String plotId) {
        BeltSurface s = surfaces.remove(plotId);
        if (s != null) {
            s.remove();
        }
        riders.entrySet().removeIf(e -> {
            if (e.getValue().plotId.equals(plotId)) {
                e.getValue().clearRides();
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        cancel(refreshTask);
        refreshTask = null;
        stopRideTask();
        removeAll();
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    private void removeAll() {
        surfaces.values().forEach(BeltSurface::remove);
        surfaces.clear();
        riders.values().forEach(BeltRider::clearRides);
        riders.clear();
    }

    private void refreshSafely() {
        try {
            refresh();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Conveyor belt refresh failed; belts reset", ex);
            removeAll();
        }
        if (!riders.isEmpty() && rideTask == null) {
            rideTask = Bukkit.getScheduler().runTaskTimer(plugin, this::rideTick, 1, 1);
        }
    }

    private void rideTick() {
        tick++;
        try {
            for (Iterator<Map.Entry<UUID, BeltRider>> it = riders.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, BeltRider> e = it.next();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null) {
                    e.getValue().clearRides();
                    it.remove();
                } else {
                    ride(p, e.getValue());
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Conveyor belt tick failed; belts reset", ex);
            removeAll();
        }
        if (riders.isEmpty()) {
            stopRideTask();
        }
    }

    private void stopRideTask() {
        cancel(rideTask);
        rideTask = null;
    }

    private static void cancel(@Nullable BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    /** Who rides where, their CPS and pack status, and healthy surfaces with the right set shown to each player. */
    private void refresh() {
        BeltSettings s = settings.get();
        if (!s.enabled()) {
            removeAll();
            return;
        }
        online.clear();
        packs.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p);
            packs.put(p.getUniqueId(), hasPack.test(p));
        }
        try {
            refreshSurfaces(s);
            refreshRiders(s);
        } finally {
            online.clear();
        }
    }

    private void refreshSurfaces(BeltSettings s) {
        for (Plot plot : plots.all()) {
            BeltSurface surface = surfaces.get(plot.id());
            if (plot.belt() == null) {
                if (surface != null) {
                    surfaces.remove(plot.id()).remove();
                }
                continue;
            }
            World world = plots.world(plot).orElse(null);
            if (world == null) {
                continue;
            }
            if (surface != null && !surface.healthy()) {
                surface.remove();
                surfaces.remove(plot.id());
                surface = null;
            }
            if (surface == null) {
                surface = spawnSurface(plot, world, s);
                if (surface == null) {
                    continue;
                }
                surfaces.put(plot.id(), surface);
            }
            for (Player p : online) {
                if (p.getWorld().equals(world)) {
                    surface.showTo(plugin, p, packs.getOrDefault(p.getUniqueId(), false));
                }
            }
        }
        surfaces.entrySet().removeIf(e -> {
            if (!plots.has(e.getKey())) {
                e.getValue().remove();
                return true;
            }
            return false;
        });
    }

    private @Nullable BeltSurface spawnSurface(Plot plot, World world, BeltSettings s) {
        try {
            return BeltSurface.spawn(plot, world, s, BeltEntities.fallbackItem(s, warnings));
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Could not spawn the belt of plot " + plot.id(), e);
            return null;
        }
    }

    private void refreshRiders(BeltSettings s) {
        riders.entrySet().removeIf(e -> {
            if (!packs.containsKey(e.getKey())) {
                e.getValue().clearRides();
                return true;
            }
            return false;
        });
        for (Player p : online) {
            UUID id = p.getUniqueId();
            String plotId = tracker.presence(id).plot();
            BeltSurface surface = plotId == null ? null : surfaces.get(plotId);
            CookiePouch pouch = sessions.get(id).orElse(null);
            BeltRider rider = riders.get(id);
            boolean eligible = surface != null && pouch != null && !inCinematic.test(id) && s.maxCookies() > 0
                    && plots.get(plotId).flatMap(plots::world).map(w -> w.equals(p.getWorld())).orElse(false);
            if (!eligible || (rider != null && !rider.plotId.equals(plotId))) {
                stopRider(id);
                rider = null;
            }
            if (!eligible) {
                continue;
            }
            if (rider == null) {
                rider = new BeltRider(plotId, p.getWorld(), surface.path);
                riders.put(id, rider);
            }
            if (rider.cpsChanged(pouch, economy.config())) { // CPS only changes with a purchase or a reload
                rider.cps = economy.totalCps(pouch);
            }
            rider.withPack = packs.getOrDefault(id, false);
        }
    }

    private void ride(Player p, BeltRider r) {
        BeltSettings s = settings.get();
        for (Iterator<BeltRider.Ride> it = r.rides.iterator(); it.hasNext(); ) {
            BeltRider.Ride ride = it.next();
            if (!ride.entity.isValid()) {
                it.remove();
                r.lane.arrived();
                continue;
            }
            if (ride.arriveTick == Long.MAX_VALUE && tick > ride.spawnTick) {
                // the client has the spawn by now: one transformation, interpolated over the whole ride
                ride.entity.setInterpolationDelay(0);
                ride.entity.setInterpolationDuration(ride.rideTicks);
                ride.entity.setTransformation(BeltEntities.cookieTransform(r.path, s.cookieScale(), s.cookiePitch(),
                        ride.thickness, r.path.length()));
                ride.arriveTick = tick + ride.rideTicks;
            } else if (tick >= ride.arriveTick) {
                double[] end = r.path.end();
                ride.entity.remove();
                it.remove();
                r.lane.arrived();
                double half = BeltEntities.halfHeight(s.cookieScale(), s.cookiePitch(), ride.thickness);
                effects.play(EFFECT_ARRIVE, new Location(r.world, end[0], end[1] + BeltEntities.RUBBER + half,
                        end[2]), p);
            }
        }
        int rideTicks = r.lane.trySpawn(tick, r.cps, s.curve(), r.path, s.maxCookies());
        if (rideTicks > 0) {
            double thickness = r.withPack ? BeltEntities.PACK_COOKIE_THICKNESS : BeltEntities.VANILLA_COOKIE_THICKNESS;
            ItemDisplay cookie = spawnCookie(p, r, s, thickness);
            if (cookie == null) {
                r.lane.arrived();
            } else {
                r.rides.add(new BeltRider.Ride(cookie, tick, rideTicks, thickness));
            }
        }
        playLoop(p, r, s);
    }

    /** A fixed-period clock per player that survives rider restarts, so a new rider never doubles a sounding loop. */
    private void playLoop(Player p, BeltRider r, BeltSettings s) {
        int period = s.soundEvery();
        long now = Bukkit.getCurrentTick();
        if (period > 0 && s.curve().running(r.cps) && sounds.loopDelay(p.getUniqueId(), SOUND_LOOP, period, now) == 0) {
            double[] m = r.path.middle();
            Location at = new Location(r.world, m[0], m[1], m[2]);
            double radius = hearingRadius.getAsDouble();
            if (p.getLocation().distanceSquared(at) <= radius * radius) {
                sounds.playLoop(p, SOUND_LOOP, at, period, now);
            }
        }
    }

    private void stopRider(UUID id) {
        BeltRider r = riders.remove(id);
        if (r != null) {
            r.clearRides();
        }
    }

    private @Nullable ItemDisplay spawnCookie(Player owner, BeltRider r, BeltSettings s, double thickness) {
        Location at = new Location(r.world, r.path.startX(), r.path.y(), r.path.startZ());
        if (!r.world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return null;
        }
        ItemStack stack = r.withPack ? BeltEntities.item(Material.COOKIE, BeltEntities.COOKIE_MODEL)
                : ItemStack.of(Material.COOKIE);
        ItemDisplay d = BeltEntities.display(r.world, at, stack, BeltEntities.cookieTransform(r.path,
                s.cookieScale(), s.cookiePitch(), thickness, 0));
        owner.showEntity(plugin, d);
        return d;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopRider(id);
        surfaces.values().forEach(s -> s.forget(id));
        sounds.forgetLoops(id); // the client stopped every sound, also the plot ambience loops
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopRider(id);
        surfaces.values().forEach(s -> s.forget(id));
    }

    /** Riders right now (debug). */
    public int riders() {
        return riders.size();
    }

    /** Belt cookies on all belts right now (debug). */
    public int cookies() {
        int n = 0;
        for (BeltRider r : riders.values()) {
            n += r.rides.size();
        }
        return n;
    }
}
