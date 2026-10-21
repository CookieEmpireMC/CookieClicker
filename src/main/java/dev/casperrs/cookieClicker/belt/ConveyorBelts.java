package dev.casperrs.cookieClicker.belt;

import dev.casperrs.cookieClicker.config.BeltSettings;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.plot.Plot;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotTracker;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.vfx.Effects;
import dev.casperrs.cookieClicker.view.StationViews;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
 * The conveyor belts (brief conveyor-belt). Per plot with a belt: one moving-surface item display per cell, shared
 * (two sets: {@code cookieclicker:conveyor_belt} for players with our pack, {@code belt.fallback-item} for the
 * others; each player is shown one set). Per player in a plot (and not watching their build cinematic): their own
 * cookies ride the belt, shown only to them, at a speed and interval from their CPS ({@link BeltCurve}), at most
 * {@code belt.max-cookies} at once ({@link BeltLane}). Each cookie is <b>one</b> interpolated transformation from the
 * hatch to the pedestal (no per-tick teleports); on arrival it is removed and {@code belt_arrive} plays. While their
 * belt runs, {@code belt_loop} plays every {@code belt.sound-every} ticks within {@code house.ambient.radius}.
 * Purely visual: nothing here touches the economy. Main thread only.
 *
 * <p>Cost: one task; per tick only the riders' cookies (at most max-cookies each) are looked at; who rides, their
 * CPS and the surfaces are checked every {@value #REFRESH_TICKS} ticks. Entities are non-persistent and tagged
 * {@value StationViews#TAG} (the enable sweep removes crash leftovers).
 */
public final class ConveyorBelts implements Listener {

    public static final String SOUND_LOOP = "belt_loop";
    public static final String EFFECT_ARRIVE = "belt_arrive";
    static final int REFRESH_TICKS = 10;
    /** The belt rubber is 1/16 thick: cookies ride this far above the surface (design/conveyor-belt.md). */
    static final double RUBBER = 0.0625;

    private static final Key BELT_MODEL = Key.key("cookieclicker", "conveyor_belt");
    private static final Key COOKIE_MODEL = Key.key("cookieclicker", "cookie");

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

    private final Map<String, Surface> surfaces = new HashMap<>();
    private final Map<UUID, Rider> riders = new HashMap<>();
    private @Nullable BukkitTask task;
    private boolean registered;
    private long tick;
    private boolean warnedItem;

    /** One plot's shared belt surface. */
    private static final class Surface {
        final BeltPath path;
        final List<ItemDisplay> withPack = new ArrayList<>();
        final List<ItemDisplay> vanilla = new ArrayList<>();
        /** Which set each player was shown (true = pack set). */
        final Map<UUID, Boolean> shown = new HashMap<>();

        Surface(BeltPath path) {
            this.path = path;
        }

        boolean healthy() {
            return withPack.stream().allMatch(Entity::isValid) && vanilla.stream().allMatch(Entity::isValid);
        }

        void remove() {
            withPack.forEach(Entity::remove);
            vanilla.forEach(Entity::remove);
            withPack.clear();
            vanilla.clear();
            shown.clear();
        }
    }

    /** One player's cookies on the belt of the plot they are in. */
    private static final class Rider {
        final String plotId;
        final World world;
        final BeltPath path;
        final BeltLane lane = new BeltLane();
        final List<Ride> rides = new ArrayList<>();
        double cps;
        boolean withPack;
        long nextSound;
        /** What {@link #cps} was computed from: buildings and upgrades only grow, so their totals show a purchase. */
        private @Nullable CookiePouch cpsPouch;
        private @Nullable Object cpsConfig;
        private long cpsOwned = -1;
        private int cpsUpgrades = -1;

        /** Whether the pouch or the balance changed since {@link #cps} was computed (and remembers the new state). */
        boolean cpsChanged(CookiePouch pouch, Object config) {
            long owned = 0;
            for (int n : pouch.ownedView().values()) {
                owned += n;
            }
            int upgrades = pouch.upgradesView().size();
            if (pouch == cpsPouch && config == cpsConfig && owned == cpsOwned && upgrades == cpsUpgrades) {
                return false;
            }
            cpsPouch = pouch;
            cpsConfig = config;
            cpsOwned = owned;
            cpsUpgrades = upgrades;
            return true;
        }

        Rider(String plotId, World world, BeltPath path) {
            this.plotId = plotId;
            this.world = world;
            this.path = path;
        }
    }

    /** One cookie: spawned at {@code spawnTick}, starts moving the tick after, arrives {@code rideTicks} later. */
    private static final class Ride {
        final ItemDisplay entity;
        final long spawnTick;
        final int rideTicks;
        long arriveTick = Long.MAX_VALUE;

        Ride(ItemDisplay entity, long spawnTick, int rideTicks) {
            this.entity = entity;
            this.spawnTick = spawnTick;
            this.rideTicks = rideTicks;
        }
    }

    /**
     * @param inCinematic   true while the player watches their build cinematic (no belt for them then)
     * @param hearingRadius {@code house.ambient.radius}: {@code belt_loop} is heard within this many blocks
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
    }

    // ---- lifecycle ------------------------------------------------------------------------------------------

    public void start() {
        if (!registered) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            registered = true;
        }
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }
    }

    /** {@code /bakery reload}: everything goes; the next refresh rebuilds it with the new plots and settings. */
    public void restart() {
        removeAll();
        warnedItem = false;
    }

    /** {@code /bakery plot setup} (or undo) changed a plot: its surface and riders are rebuilt on the next refresh. */
    public void plotChanged(String plotId) {
        Surface s = surfaces.remove(plotId);
        if (s != null) {
            s.remove();
        }
        for (UUID id : List.copyOf(riders.keySet())) {
            if (riders.get(id).plotId.equals(plotId)) {
                stopRider(id);
            }
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        removeAll();
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
    }

    private void removeAll() {
        surfaces.values().forEach(Surface::remove);
        surfaces.clear();
        for (UUID id : List.copyOf(riders.keySet())) {
            stopRider(id);
        }
    }

    // ---- tick ----------------------------------------------------------------------------------------------

    private void tick() {
        tick++;
        try {
            if (tick % REFRESH_TICKS == 1) {
                refresh();
            }
            // ride() never changes the riders map, so no copy per tick
            for (Iterator<Map.Entry<UUID, Rider>> it = riders.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Rider> e = it.next();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null) {
                    clearRides(e.getValue());
                    it.remove();
                } else {
                    ride(p, e.getValue());
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Conveyor belt tick failed; belts reset", ex);
            removeAll();
        }
    }

    /** Who rides where, their CPS and pack status, and healthy surfaces with the right set shown to each player. */
    private void refresh() {
        BeltSettings s = settings.get();
        if (!s.enabled()) {
            removeAll();
            return;
        }
        Map<UUID, Boolean> packs = new HashMap<>(); // hasPack once per player per refresh
        for (Player p : Bukkit.getOnlinePlayers()) {
            packs.put(p.getUniqueId(), hasPack.test(p));
        }
        // surfaces
        for (Plot plot : plots.all()) {
            Surface surface = surfaces.get(plot.id());
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
            for (Player p : world.getPlayers()) {
                boolean pack = packs.getOrDefault(p.getUniqueId(), false);
                Boolean before = surface.shown.put(p.getUniqueId(), pack);
                if (before == null || before != pack) {
                    (pack ? surface.withPack : surface.vanilla).forEach(e -> p.showEntity(plugin, e));
                    (pack ? surface.vanilla : surface.withPack).forEach(e -> p.hideEntity(plugin, e));
                }
            }
        }
        for (String id : List.copyOf(surfaces.keySet())) {
            if (!plots.has(id)) {
                surfaces.remove(id).remove();
            }
        }

        // riders
        for (UUID id : List.copyOf(riders.keySet())) {
            if (Bukkit.getPlayer(id) == null) {
                stopRider(id);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            String plotId = tracker.presence(id).plot();
            Surface surface = plotId == null ? null : surfaces.get(plotId);
            CookiePouch pouch = sessions.get(id).orElse(null);
            Rider rider = riders.get(id);
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
                rider = new Rider(plotId, p.getWorld(), surface.path);
                rider.nextSound = tick;
                riders.put(id, rider);
            }
            if (rider.cpsChanged(pouch, economy.config())) { // CPS only changes with a purchase or a reload
                rider.cps = economy.totalCps(pouch);
            }
            rider.withPack = packs.getOrDefault(id, false);
        }
    }

    private void ride(Player p, Rider r) {
        BeltSettings s = settings.get();
        double half = s.cookieScale() / 2;
        for (Iterator<Ride> it = r.rides.iterator(); it.hasNext(); ) {
            Ride ride = it.next();
            if (!ride.entity.isValid()) {
                it.remove();
                r.lane.arrived();
                continue;
            }
            if (ride.arriveTick == Long.MAX_VALUE && tick > ride.spawnTick) {
                // the client has the spawn by now: one transformation, interpolated over the whole ride
                ride.entity.setInterpolationDelay(0);
                ride.entity.setInterpolationDuration(ride.rideTicks);
                ride.entity.setTransformation(cookieTransform(r.path, s.cookieScale(), r.path.length()));
                ride.arriveTick = tick + ride.rideTicks;
            } else if (tick >= ride.arriveTick) {
                double[] end = r.path.end();
                ride.entity.remove();
                it.remove();
                r.lane.arrived();
                effects.play(EFFECT_ARRIVE, new Location(r.world, end[0], end[1] + RUBBER + half, end[2]), p);
            }
        }
        int rideTicks = r.lane.trySpawn(tick, r.cps, s.curve(), r.path, s.maxCookies());
        if (rideTicks > 0) {
            ItemDisplay cookie = spawnCookie(p, r, s);
            if (cookie == null) {
                r.lane.arrived();
            } else {
                r.rides.add(new Ride(cookie, tick, rideTicks));
            }
        }
        if (s.soundEvery() > 0 && s.curve().running(r.cps) && tick >= r.nextSound) {
            r.nextSound = tick + s.soundEvery();
            double[] m = r.path.middle();
            Location at = new Location(r.world, m[0], m[1], m[2]);
            double radius = hearingRadius.getAsDouble();
            if (p.getLocation().distanceSquared(at) <= radius * radius) {
                sounds.play(p, SOUND_LOOP, at);
            }
        }
    }

    private void stopRider(UUID id) {
        Rider r = riders.remove(id);
        if (r != null) {
            clearRides(r);
        }
    }

    private static void clearRides(Rider r) {
        r.rides.forEach(ride -> ride.entity.remove());
        r.rides.clear();
    }

    // ---- spawning --------------------------------------------------------------------------------------------

    private @Nullable Surface spawnSurface(Plot plot, World world, BeltSettings s) {
        BeltPath path = BeltPath.of(plot.belt(), plot.pedestal());
        if (path == null) {
            return null;
        }
        for (Plot.Cell c : plot.belt().cells()) {
            if (!world.isChunkLoaded(c.x() >> 4, c.z() >> 4)) {
                return null; // spawned once the belt's chunks are loaded
            }
        }
        Surface surface = new Surface(path);
        Material fallback = fallbackItem(s);
        // model north (-z) turned to the travel direction (+ belt.surface-yaw); our model's bottom is at its origin,
        // a vanilla block item is centred on it, so the fallback is lifted half a block onto the frame
        Quaternionf turn = new Quaternionf().rotateY((float) (Math.atan2(-path.dirX(), -path.dirZ())
                - Math.toRadians(s.surfaceYaw())));
        Transformation model = new Transformation(new Vector3f(), turn, new Vector3f(1, 1, 1), new Quaternionf());
        Transformation vanilla = new Transformation(new Vector3f(0, 0.5f, 0), turn, new Vector3f(1, 1, 1),
                new Quaternionf());
        try {
            for (int i = 0; i < path.length(); i++) {
                double[] c = path.cellCentre(i);
                Location at = new Location(world, c[0], c[1], c[2]);
                surface.withPack.add(display(world, at, item(fallback, BELT_MODEL), model));
                surface.vanilla.add(display(world, at, ItemStack.of(fallback), vanilla));
            }
        } catch (RuntimeException e) {
            surface.remove();
            plugin.getLogger().log(Level.WARNING, "Could not spawn the belt of plot " + plot.id(), e);
            return null;
        }
        return surface;
    }

    private @Nullable ItemDisplay spawnCookie(Player owner, Rider r, BeltSettings s) {
        Location at = new Location(r.world, r.path.startX(), r.path.y(), r.path.startZ());
        if (!r.world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return null;
        }
        ItemStack stack = r.withPack ? item(Material.COOKIE, COOKIE_MODEL) : ItemStack.of(Material.COOKIE);
        ItemDisplay d = display(r.world, at, stack, cookieTransform(r.path, s.cookieScale(), 0));
        owner.showEntity(plugin, d);
        return d;
    }

    /**
     * The cookie {@code along} blocks down the belt, standing on the rubber ({@link #RUBBER} above the surface), its
     * face toward the pedestal.
     */
    static Transformation cookieTransform(BeltPath path, double scale, double along) {
        float sc = (float) scale;
        return new Transformation(
                new Vector3f((float) (path.dirX() * along), (float) RUBBER + sc / 2, (float) (path.dirZ() * along)),
                new Quaternionf().rotateY((float) path.travelAngle()), new Vector3f(sc, sc, sc), new Quaternionf());
    }

    /** Hidden from everyone at spawn (shown per player), non-persistent, tagged for the sweep. */
    private static ItemDisplay display(World world, Location at, ItemStack stack, Transformation t) {
        return world.spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            d.addScoreboardTag(StationViews.TAG);
            d.setItemStack(stack);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(t);
        });
    }

    private static ItemStack item(Material base, Key model) {
        ItemStack stack = ItemStack.of(base);
        stack.setData(DataComponentTypes.ITEM_MODEL, model);
        return stack;
    }

    private Material fallbackItem(BeltSettings s) {
        Material m = Material.matchMaterial(s.fallbackItem());
        if (m == null || !m.isItem() || m.isAir()) {
            if (!warnedItem) {
                warnedItem = true;
                plugin.getLogger().warning("config.yml: belt.fallback-item '" + s.fallbackItem()
                        + "' is not an item; using BLACK_CARPET");
            }
            return Material.BLACK_CARPET;
        }
        return m;
    }

    // ---- events -----------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopRider(id);
        surfaces.values().forEach(s -> s.shown.remove(id));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        stopRider(id);
        surfaces.values().forEach(s -> s.shown.remove(id));
    }

    /** Riders right now (for the tester / debug). */
    public int riders() {
        return riders.size();
    }

    /** Belt cookies on all belts right now (for the tester / debug). */
    public int cookies() {
        return riders.values().stream().mapToInt(r -> r.rides.size()).sum();
    }
}
