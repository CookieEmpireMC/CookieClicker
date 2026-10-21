package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.StationVisualSettings;
import dev.casperrs.cookieClicker.config.StationVisualSettings.Offset;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.session.SessionListener;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.station.Station;
import dev.casperrs.cookieClicker.station.StationListener;
import dev.casperrs.cookieClicker.station.StationRegistry;
import dev.casperrs.cookieClicker.text.Messages;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Spawns, updates and removes every player's station visuals (cookie, pedestal, panel) with a proximity lifecycle:
 * a task every {@code station.refresh-ticks} spawns a player's view when they are online, loaded and within
 * {@code station.view-distance} of a station, and removes it when they leave. Spawning always ends with
 * {@code showEntity}, so nothing depends on remembered visibility. Quit, world change, station removal, chunk
 * unload, reload and disable remove views right away; {@link #sweep(boolean)} removes leftovers. A second task drives the
 * cookie's spin/bob segments. Main thread only.
 */
public final class StationViews implements Listener, StationListener, SessionListener {

    /** Shared sweep tag of every plugin entity (also used by the vfx engine). */
    public static final String TAG = "cookieclicker";

    private static final Key COOKIE_MODEL = Key.key("cookieclicker", "cookie");
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Plugin plugin;
    private final Supplier<StationVisualSettings> settings;
    private final StationRegistry stations;
    private final SessionManager sessions;
    private final Economy economy;
    private final Messages messages;
    private final Predicate<Player> hasPack;
    private final Logger log;
    private final NamespacedKey entityKey;

    private final Map<UUID, Map<String, StationView>> byOwner = new HashMap<>();
    private final Map<UUID, StationView> byEntity = new HashMap<>();
    private final Map<UUID, ClickTarget> targets = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private @Nullable BukkitTask refreshTask;
    private @Nullable BukkitTask motionTask;
    private CookieMotion motion;
    private long lastMotionRun = Long.MIN_VALUE;
    private boolean running;

    public StationViews(Plugin plugin, Supplier<StationVisualSettings> settings, StationRegistry stations,
                        SessionManager sessions, Economy economy, Messages messages, Predicate<Player> hasPack) {
        this.plugin = plugin;
        this.settings = settings;
        this.stations = stations;
        this.sessions = sessions;
        this.economy = economy;
        this.messages = messages;
        this.hasPack = hasPack;
        this.log = plugin.getLogger();
        this.entityKey = new NamespacedKey(plugin, "station");
        this.motion = CookieMotion.of(settings.get().cookie());
    }

    // ---- lifecycle ------------------------------------------------------------------------------------------

    /** Registers listeners and starts the tasks (enable). Call {@link #restart()} after a reload. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        stations.addListener(this);
        sessions.addListener(this);
        startTasks();
    }

    /** {@code /bakery reload}: removes every view (layout/settings may have changed); the next refresh respawns. */
    public void restart() {
        cancelTasks();
        removeAll();
        warned.clear();
        motion = CookieMotion.of(settings.get().cookie());
        if (running) {
            startTasks();
        }
    }

    /**
     * Disable: stops the tasks, removes every view and unregisters. Runs before the sessions shut down and
     * doesn't rely on {@code onUnloaded} (which {@code SessionManager.shutdown} doesn't fire).
     */
    public void shutdown() {
        cancelTasks();
        removeAll();
        if (running) {
            HandlerList.unregisterAll(this);
            stations.removeListener(this);
            sessions.removeListener(this);
            running = false;
        }
        sweep(false);
    }

    private void startTasks() {
        StationVisualSettings s = settings.get();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1, s.refreshTicks());
        int seg = s.cookie().segmentTicks();
        motionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::motionTick, seg, seg);
    }

    private void cancelTasks() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (motionTask != null) {
            motionTask.cancel();
            motionTask = null;
        }
    }

    /**
     * Removes leftover station entities in loaded worlds (ours by PDC key, not part of a live view).
     *
     * @param anyTagged also remove everything else tagged {@value #TAG} (enable only, before the vfx engine
     *                  starts, so crash leftovers of effect entities go too)
     * @return how many were removed
     */
    public int sweep(boolean anyTagged) {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e.getScoreboardTags().contains(TAG) && !byEntity.containsKey(e.getUniqueId())
                        && (anyTagged || e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE))) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("Removed " + removed + " leftover station entit" + (removed == 1 ? "y" : "ies"));
        }
        return removed;
    }

    // ---- queries (click router) -----------------------------------------------------------------------------

    public Optional<ClickTarget> target(UUID entityId) {
        return Optional.ofNullable(targets.get(entityId));
    }

    @Nullable StationView view(UUID owner, String stationId) {
        Map<String, StationView> views = byOwner.get(owner);
        return views == null ? null : views.get(stationId);
    }

    /** The current visual settings (reload swaps them). */
    StationVisualSettings settings() {
        return settings.get();
    }

    /** Live view entities (for the tester / debug log). */
    public int trackedEntities() {
        return byEntity.size();
    }

    // ---- refresh (proximity lifecycle) ----------------------------------------------------------------------

    private void refresh() {
        StationVisualSettings s = settings.get();
        // owners who went offline without a quit event reaching us
        for (UUID owner : List.copyOf(byOwner.keySet())) {
            if (Bukkit.getPlayer(owner) == null) {
                removeOwner(owner);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player, s);
        }
    }

    /** One player's proximity lifecycle: spawn, heal, update or remove their view at every station. */
    private void refresh(Player player, StationVisualSettings s) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        if (pouch == null) {
            removeOwner(player.getUniqueId()); // not loaded (yet, or load failed): no UI to click
            return;
        }
        double maxSq = s.viewDistance() * s.viewDistance();
        Location at = player.getLocation();
        String world = player.getWorld().getKey().asString();
        for (Station station : stations.all()) {
            StationView view = view(player.getUniqueId(), station.id());
            boolean inRange = station.distanceSquared(world, at.getX(), at.getY(), at.getZ()) <= maxSq
                    && chunksLoaded(player.getWorld(), station, s);
            if (!inRange) {
                if (view != null) {
                    remove(view);
                }
                continue;
            }
            if (view != null && !view.healthy()) {
                remove(view);
                view = null;
            }
            if (view == null) {
                view = spawn(player, station, s, pouch);
                if (view == null) {
                    continue;
                }
            } else {
                heal(player, view);
            }
            updatePack(player, view);
            updateRows(view, pouch);
        }
    }

    /**
     * Refresh one player's panel rows now (after a click or purchase, and from the 1 Hz ticker right after
     * production). Rows whose {@link RowState} didn't change send nothing.
     */
    public void refreshPanels(Player player, CookiePouch pouch) {
        Map<String, StationView> views = byOwner.get(player.getUniqueId());
        if (views != null) {
            views.values().forEach(v -> updateRows(v, pouch));
        }
    }

    private boolean chunksLoaded(World world, Station station, StationVisualSettings s) {
        StationFrame frame = StationFrame.of(station);
        return chunkLoaded(world, frame, Offset.ZERO) && chunkLoaded(world, frame, cookieOffset(station, s))
                && chunkLoaded(world, panelFrame(station), panelOffset(station, s));
    }

    // With a plot layout (station.shop() set) the cookie stands right on the station point and the panel on the shop
    // point: only the "up" part of the station.cookie/pedestal/panel offsets is used. Without one, the offsets apply.

    private static Offset cookieOffset(Station station, StationVisualSettings s) {
        return station.shop() == null ? s.cookie().offset() : upOnly(s.cookie().offset());
    }

    private static Offset panelOffset(Station station, StationVisualSettings s) {
        return station.shop() == null ? s.panel().offset() : upOnly(s.panel().offset());
    }

    private static StationFrame panelFrame(Station station) {
        Station.Anchor shop = station.shop();
        return shop == null ? StationFrame.of(station) : new StationFrame(shop.x(), shop.y(), shop.z(), shop.yaw());
    }

    private static Offset upOnly(Offset o) {
        return new Offset(0, o.up(), 0);
    }

    private static boolean chunkLoaded(World world, StationFrame frame, Offset o) {
        double[] p = frame.point(o);
        return world.isChunkLoaded((int) Math.floor(p[0]) >> 4, (int) Math.floor(p[2]) >> 4);
    }

    /** Cheap self-heal: the owner should always see their own view. */
    private void heal(Player owner, StationView view) {
        for (Entity e : view.entities()) {
            if (!owner.canSee(e)) {
                owner.showEntity(plugin, e);
            }
        }
    }

    // ---- spawning -------------------------------------------------------------------------------------------

    private @Nullable StationView spawn(Player owner, Station station, StationVisualSettings s, CookiePouch pouch) {
        World world = owner.getWorld();
        StationFrame frame = StationFrame.of(station);
        boolean pack = hasPack.test(owner);
        List<Entity> spawned = new ArrayList<>();
        try {
            StationVisualSettings.Cookie c = s.cookie();
            double[] centre = frame.point(cookieOffset(station, s));
            float scale = c.scale();
            ItemDisplay cookie = spawn(world, centre, frame.facingViewers(0), ItemDisplay.class, owner, spawned, d -> {
                d.setItemStack(cookieStack(pack));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(c.viewRange());
                d.setBrightness(new Display.Brightness(c.brightness(), c.brightness()));
                long now = Bukkit.getCurrentTick();
                d.setTransformation(new Transformation(new Vector3f(0, (float) motion.bob(now), 0),
                        new Quaternionf().rotationY((float) motion.angle(now)),
                        new Vector3f(scale, scale, scale), new Quaternionf()));
            });
            float boxWidth = (float) (scale + c.hitboxMargin());
            float boxHeight = (float) (scale + 2 * c.bobAmplitude() + c.hitboxMargin());
            double[] boxAt = {centre[0], centre[1] - boxHeight / 2, centre[2]};
            Interaction cookieBox = spawn(world, boxAt, 0, Interaction.class, owner, spawned, i -> {
                i.setInteractionWidth(boxWidth);
                i.setInteractionHeight(boxHeight);
                i.setResponsive(true);
            });

            StationVisualSettings.Pedestal ped = s.pedestal();
            BlockDisplay pedestal = spawnPedestal(world, frame, station.shop() == null ? ped
                    : new StationVisualSettings.Pedestal(ped.enabled(), ped.block(), upOnly(ped.offset()), ped.width(),
                            ped.height()), owner, spawned);

            StationVisualSettings.Panel p = s.panel();
            StationFrame pf = panelFrame(station);
            Offset po = panelOffset(station, s);
            float textYaw = pf.facingViewers(p.textYawOffset());
            float iconYaw = pf.facingViewers(p.iconYawOffset());
            TextDisplay header = spawn(world, pf.point(po), textYaw, TextDisplay.class, owner, spawned, t -> {
                styleText(t, p, p.headerScale(), p.color("header"));
                t.setAlignment(TextDisplay.TextAlignment.CENTER);
                t.text(messages.component("panel.header"));
            });

            List<StationView.Row> rows = new ArrayList<>();
            int index = 0;
            for (String buildingId : economy.config().buildings().keySet()) {
                Offset anchor = new Offset(po.right(), po.up() - p.firstRowDrop() - index * p.rowPitch(), po.forward());
                index++;
                ItemDisplay icon = spawn(world, pf.point(anchor, p.iconOffset()), iconYaw, ItemDisplay.class, owner,
                        spawned, d -> {
                            d.setItemStack(iconStack(buildingId, pack));
                            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                            d.setBillboard(Display.Billboard.FIXED);
                            d.setViewRange(p.viewRange());
                            float is = p.iconScale();
                            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                                    new Vector3f(is, is, is), new Quaternionf()));
                        });
                TextDisplay text = spawn(world, pf.point(anchor, p.textOffset()), textYaw, TextDisplay.class, owner,
                        spawned, t -> {
                            styleText(t, p, p.textScale(), p.color("unaffordable"));
                            t.setAlignment(TextDisplay.TextAlignment.LEFT);
                        });
                double[] boxBottom = pf.point(anchor, p.hitboxOffset());
                Interaction box = spawn(world, boxBottom, 0, Interaction.class, owner, spawned, i -> {
                    i.setInteractionWidth(p.hitboxWidth());
                    i.setInteractionHeight(p.hitboxHeight());
                    i.setResponsive(true);
                });
                double[] rowCentre = {boxBottom[0], boxBottom[1] + p.hitboxHeight() / 2, boxBottom[2]};
                rows.add(new StationView.Row(buildingId, icon, text, box, rowCentre));
            }

            StationView view = new StationView(owner.getUniqueId(), station, cookie, cookieBox, centre, pedestal,
                    header, rows, pack);
            register(view);
            updateRows(view, pouch);
            return view;
        } catch (RuntimeException e) {
            spawned.forEach(Entity::remove);
            warnOnce("spawn", "Could not spawn the bakery view at " + station.id() + ": " + e);
            return null;
        }
    }

    private @Nullable BlockDisplay spawnPedestal(World world, StationFrame frame, StationVisualSettings.Pedestal ped,
                                                 Player owner, List<Entity> spawned) {
        if (!ped.enabled()) {
            return null;
        }
        BlockData block;
        try {
            block = Bukkit.createBlockData(ped.block());
        } catch (IllegalArgumentException e) {
            warnOnce("pedestal", "config.yml: station.pedestal.block '" + ped.block() + "' is not a block; no pedestal");
            return null;
        }
        float w = (float) ped.width();
        float h = (float) ped.height();
        // block displays grow from their corner: shift by half the width so the offset is the bottom centre
        return spawn(world, frame.point(ped.offset()), 0, BlockDisplay.class, owner, spawned, d -> {
            d.setBlock(block);
            d.setTransformation(new Transformation(new Vector3f(-w / 2, 0, -w / 2), new Quaternionf(),
                    new Vector3f(w, h, w), new Quaternionf()));
        });
    }

    private void styleText(TextDisplay t, StationVisualSettings.Panel p, float scale, int argb) {
        t.setBillboard(Display.Billboard.FIXED);
        t.setLineWidth(p.lineWidth());
        t.setSeeThrough(p.textSeeThrough());
        t.setShadowed(p.textShadowed());
        t.setDefaultBackground(p.textDefaultBackground());
        t.setBackgroundColor(Color.fromARGB(argb));
        t.setBrightness(new Display.Brightness(p.textBrightness(), p.textBrightness()));
        t.setViewRange(p.viewRange());
        t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    /** Spawn-in-consumer (never visible to anyone else, not even for a tick), then show to the owner. */
    private <T extends Entity> T spawn(World world, double[] p, float yaw, Class<T> type, Player owner,
                                       List<Entity> spawned, Consumer<? super T> setup) {
        Location loc = new Location(world, p[0], p[1], p[2], yaw, 0);
        T entity = world.spawn(loc, type, e -> {
            e.setPersistent(false);
            e.setVisibleByDefault(false);
            e.addScoreboardTag(TAG);
            e.getPersistentDataContainer().set(entityKey, PersistentDataType.BYTE, (byte) 1);
            setup.accept(e);
        });
        spawned.add(entity);
        owner.showEntity(plugin, entity);
        return entity;
    }

    private void register(StationView view) {
        byOwner.computeIfAbsent(view.owner, k -> new HashMap<>()).put(view.station.id(), view);
        for (Entity e : view.entities()) {
            byEntity.put(e.getUniqueId(), view);
        }
        targets.put(view.cookieHitbox.getUniqueId(),
                new ClickTarget(view.owner, view.station.id(), ClickTarget.Kind.COOKIE, null));
        for (StationView.Row r : view.rows) {
            targets.put(r.hitbox.getUniqueId(), new ClickTarget(view.owner, view.station.id(), ClickTarget.Kind.ROW, r.buildingId));
        }
    }

    // ---- items ----------------------------------------------------------------------------------------------

    private static ItemStack cookieStack(boolean withPack) {
        ItemStack stack = ItemStack.of(Material.COOKIE);
        if (withPack) {
            stack.setData(DataComponentTypes.ITEM_MODEL, COOKIE_MODEL);
        }
        return stack;
    }

    private ItemStack iconStack(String buildingId, boolean withPack) {
        String name = settings.get().panel().fallbackFor(buildingId);
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) {
            warnOnce("icon:" + buildingId, "config.yml: station.panel.icons." + buildingId
                    + ".fallback-material '" + name + "' is not an item; using COOKIE");
            material = Material.COOKIE;
        }
        ItemStack stack = ItemStack.of(material);
        if (withPack) {
            stack.setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", "building/" + buildingId));
        }
        return stack;
    }

    /** Swaps item stacks when the owner's pack status changed (e.g. the pack finished loading after spawn). */
    private void updatePack(Player owner, StationView view) {
        boolean pack = hasPack.test(owner);
        if (pack == view.withPack) {
            return;
        }
        view.withPack = pack;
        view.cookie.setItemStack(cookieStack(pack));
        for (StationView.Row r : view.rows) {
            r.icon.setItemStack(iconStack(r.buildingId, pack));
        }
    }

    // ---- panel rows -----------------------------------------------------------------------------------------

    private void updateRows(StationView view, CookiePouch pouch) {
        StationVisualSettings.Panel p = settings.get().panel();
        for (StationView.Row row : view.rows) {
            RowState state = RowState.of(economy, pouch, row.buildingId);
            if (state.equals(row.last) || !row.text.isValid()) {
                continue; // unchanged: no metadata packets
            }
            row.last = state;
            Component text = messages.component(state.style().messagePath(),
                    Placeholder.unparsed("name", PLAIN.serialize(messages.buildingName(row.buildingId))),
                    messages.number("owned", state.owned()),
                    messages.number("cost", state.cost()),
                    messages.number("cps", state.cps()),
                    messages.number("needed", state.needed()));
            row.text.text(text);
            alignLeft(row.text, text, p);
            row.text.setBackgroundColor(Color.fromARGB(p.color(state.style().key())));
            int light = state.style() == RowState.Style.AFFORDABLE ? p.iconBrightAffordable() : p.iconBrightUnaffordable();
            row.icon.setBrightness(new Display.Brightness(light, light));
        }
    }

    /**
     * A text display is drawn centred on its position; shift it right by half its estimated rendered width so the
     * left edge of its background sits on the row's text offset, whatever the text's length or wrapping.
     */
    private static void alignLeft(TextDisplay display, Component text, StationVisualSettings.Panel p) {
        float shift = p.textLeftEdge() ? TextWidth.leftEdgeShift(TextWidth.width(text, p.lineWidth()), p.textScale()) : 0;
        float scale = p.textScale();
        display.setTransformation(new Transformation(new Vector3f(shift, 0, 0), new Quaternionf(),
                new Vector3f(scale, scale, scale), new Quaternionf()));
    }

    // ---- cookie motion --------------------------------------------------------------------------------------

    private void motionTick() {
        long now = Bukkit.getCurrentTick();
        lastMotionRun = now;
        for (Map<String, StationView> views : byOwner.values()) {
            for (StationView v : views.values()) {
                if (v.lastSegmentTick < now - 1) { // a click may have sent this segment early
                    segment(v, now);
                }
            }
        }
    }

    /**
     * Before a pop on {@code view}'s cookie: if the motion task is about to send its segment (this tick or the
     * next, which would land in the same metadata packet), send it now so the pop's {@code setTransformation}
     * is the last one and wins.
     */
    void beforePop(StationView view) {
        long now = Bukkit.getCurrentTick();
        long due = lastMotionRun == Long.MIN_VALUE ? Long.MIN_VALUE : lastMotionRun + settings.get().cookie().segmentTicks();
        if ((due == now || due == now + 1) && view.lastSegmentTick < now - 1) {
            segment(view, now);
        }
    }

    /** One interpolated segment toward the motion at {@code now + segment}; keeps the current scale (pop rule). */
    private void segment(StationView v, long now) {
        if (!v.cookie.isValid()) {
            return;
        }
        int seg = settings.get().cookie().segmentTicks();
        long target = now + seg;
        Transformation current = v.cookie.getTransformation();
        v.cookie.setInterpolationDelay(0);
        v.cookie.setInterpolationDuration(seg);
        v.cookie.setTransformation(new Transformation(new Vector3f(0, (float) motion.bob(target), 0),
                new Quaternionf().rotationY((float) motion.angle(target)),
                new Vector3f(current.getScale()), new Quaternionf(current.getRightRotation())));
        v.lastSegmentTick = now;
    }

    // ---- removal --------------------------------------------------------------------------------------------

    private void remove(StationView view) {
        Map<String, StationView> views = byOwner.get(view.owner);
        if (views != null && views.get(view.station.id()) == view) {
            views.remove(view.station.id());
            if (views.isEmpty()) {
                byOwner.remove(view.owner);
            }
        }
        for (Entity e : view.entities()) {
            byEntity.remove(e.getUniqueId());
            targets.remove(e.getUniqueId());
        }
        view.remove();
    }

    private void removeOwner(UUID owner) {
        Map<String, StationView> views = byOwner.get(owner);
        if (views != null) {
            List.copyOf(views.values()).forEach(this::remove);
        }
    }

    private void removeStation(String stationId) {
        for (Map<String, StationView> views : List.copyOf(byOwner.values())) {
            StationView v = views.get(stationId);
            if (v != null) {
                remove(v);
            }
        }
    }

    private void removeAll() {
        for (UUID owner : List.copyOf(byOwner.keySet())) {
            removeOwner(owner);
        }
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            log.warning(message);
        }
    }

    // ---- hooks ----------------------------------------------------------------------------------------------

    @Override
    public void onStationAdded(Station station) {
        // the next refresh spawns it for players in range
    }

    @Override
    public void onStationRemoved(Station station) {
        removeStation(station.id());
    }

    /** Spawn the bakery as soon as the session loads instead of on the next refresh. */
    @Override
    public void onLoaded(UUID player, CookiePouch pouch) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            refresh(online, settings.get());
        }
    }

    @Override
    public void onUnloaded(UUID player) {
        removeOwner(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removeOwner(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        removeOwner(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to.getWorld() != null && !to.getWorld().equals(event.getFrom().getWorld())) {
            removeOwner(event.getPlayer().getUniqueId());
        }
    }

    /** A chunk with one of our entities unloads: drop that whole view; the refresh respawns it when back in range. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        Set<StationView> hit = new HashSet<>();
        for (Entity e : event.getEntities()) {
            StationView v = byEntity.get(e.getUniqueId());
            if (v != null) {
                hit.add(v);
            }
        }
        hit.forEach(this::remove);
    }

    /** Non-persistent entities never come back from disk; anything of ours that does is a leftover. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            // the tag check is cheap and filters out almost every entity before the PDC is touched
            if (e.getScoreboardTags().contains(TAG) && e.getPersistentDataContainer().has(entityKey, PersistentDataType.BYTE)
                    && !byEntity.containsKey(e.getUniqueId())) {
                e.remove();
            }
        }
    }
}
