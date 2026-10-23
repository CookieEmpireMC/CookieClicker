package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.config.BasketSettings;
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
import dev.casperrs.cookieClicker.vfx.PopMotion;
import dev.casperrs.cookieClicker.text.TextPadding;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Spawns, updates and removes every player's station visuals (cookie, pedestal, the buildings and click upgrades
 * panels, the upgrade shelf) with a proximity lifecycle: a task every {@code station.refresh-ticks} spawns a player's
 * view when they are online, loaded and within {@code station.view-distance} of a station, and removes it when they
 * leave. Spawning always ends with {@code showEntity}, so nothing depends on remembered visibility. Quit, world
 * change, station removal, chunk unload, reload and disable remove views right away; {@link #sweep(boolean)} removes
 * leftovers. A second task runs every tick: the cookie's spin/bob segments, the panels' price rolls and the shelf
 * icons' flights. Main thread only.
 */
public final class StationViews implements Listener, StationListener, SessionListener {

    /** Shared sweep tag of every plugin entity (also used by the vfx engine). */
    public static final String TAG = "cookieclicker";

    private static final Key COOKIE_MODEL = Key.key("cookieclicker", "cookie");
    private static final Key BASKET_MODEL = Key.key("cookieclicker", "cookie_basket");
    /** Ticks between a buy and the start of its icon's flight (the {@code upgrade_bought} sparkle comes first). */
    private static final int FLY_DELAY_TICKS = 2;

    private final Plugin plugin;
    private final Supplier<StationVisualSettings> settings;
    private final StationRegistry stations;
    private final SessionManager sessions;
    private final Economy economy;
    private final Messages messages;
    private final Predicate<Player> hasPack;
    private final Logger log;
    private final NamespacedKey entityKey;
    private final BulkModes modes = new BulkModes();
    private final PanelPages pages = new PanelPages();
    private final PanelUpdater panels;

    private final Map<UUID, Map<String, StationView>> byOwner = new HashMap<>();
    private final Map<UUID, StationView> byEntity = new HashMap<>();
    private final Map<UUID, ClickTarget> targets = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private @Nullable BukkitTask refreshTask;
    private @Nullable BukkitTask animationTask;
    private CookieMotion motion;
    private BiConsumer<Player, Location> shelfLanding = (player, at) -> { };
    private BiConsumer<Player, Location> basketTierUp = (player, at) -> { };
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
        this.panels = new PanelUpdater(settings, economy, messages, modes, this::iconStack, pages);
    }

    /** Called when a bought upgrade's icon lands on its shelf slot (effect + sound live with the interactions). */
    public void setShelfLanding(BiConsumer<Player, Location> shelfLanding) {
        this.shelfLanding = shelfLanding;
    }

    /** Called when the owner's basket reaches a higher tier (effect + sound live with the interactions). */
    public void setBasketTierUp(BiConsumer<Player, Location> basketTierUp) {
        this.basketTierUp = basketTierUp;
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
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::animationTick, 1, 1);
    }

    private void cancelTasks() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
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

    // ---- queries (click router, interactions) ---------------------------------------------------------------

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

    /** The player's bulk mode on a panel (session only, default ×1). */
    BulkMode mode(UUID player, PanelKind panel) {
        return modes.get(player, panel);
    }

    /** The toggle was clicked: next mode, then the panel rolls its prices to the new amounts. */
    BulkMode cycleMode(Player player, PanelKind panel) {
        return modes.cycle(player.getUniqueId(), panel);
    }

    /** A toggle label was clicked: that mode. */
    void setMode(Player player, PanelKind panel, BulkMode mode) {
        modes.set(player.getUniqueId(), panel, mode);
    }

    /**
     * A ▲ (-1) / ▼ (+1) page label was clicked: scrolls that panel one row for this player. False when it can't
     * scroll that way (already at the end, or the panel doesn't page).
     */
    boolean scroll(StationView view, PanelKind panel, CookiePouch pouch, int delta) {
        return panels.scroll(view, view.panel(panel), pouch, delta);
    }

    /**
     * What the player's eye ray hits first at {@code view} within {@code maxDistance}: a panel row or toggle (by
     * its rendered bounds) or the cookie's box. Clicks on any of the view's boxes are resolved this way, so a box
     * that overlaps another (or sits in front of it) never steals the click.
     */
    @Nullable Aim aim(StationView view, Vector eye, Vector dir, double maxDistance) {
        StationVisualSettings s = settings.get();
        Aim best = null;
        RayTraceResult cookie = view.cookieHitbox.isValid()
                ? view.cookieHitbox.getBoundingBox().rayTrace(eye, dir, maxDistance) : null;
        if (cookie != null) {
            best = new Aim(null, null, cookie.getHitPosition(), cookie.getHitPosition().distance(eye));
        }
        for (StationView.Panel panel : view.panels()) {
            Offset e = panel.frame.local(eye.getX(), eye.getY(), eye.getZ(), panel.anchor);
            Offset d = panel.frame.direction(dir.getX(), dir.getY(), dir.getZ());
            PanelLayout.Hit hit = PanelLayout.of(s, panel.rows.size())
                    .pick(e, d, maxDistance, panel.rowBoxes(), panel.toggleBox);
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = new Aim(panel.kind, hit, null, hit.distance());
            }
        }
        return best;
    }

    /**
     * The first thing an eye ray hits at a view: a line of panel {@code panel} ({@code hit}), or the cookie
     * ({@code cookieAt} = where the ray enters its box).
     */
    record Aim(@Nullable PanelKind panel, PanelLayout.@Nullable Hit hit, @Nullable Vector cookieAt, double distance) {
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
            // a pack change (e.g. the pack finished loading after spawn) swaps basket and block: respawn
            if (view != null && (!view.healthy() || view.withPack != hasPack.test(player))) {
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
            update(player, view, pouch, false);
        }
    }

    /**
     * Refresh one player's panels and shelf now (from the 1 Hz ticker right after production, and after a cookie
     * click). Rows whose values didn't change send nothing; changed prices roll.
     */
    public void refreshPanels(Player player, CookiePouch pouch) {
        refreshPanels(player, pouch, false);
    }

    /** @param pulse a player action (purchase, toggle) changed the values: rolling rows also pulse */
    void refreshPanels(Player player, CookiePouch pouch, boolean pulse) {
        Map<String, StationView> views = byOwner.get(player.getUniqueId());
        if (views != null) {
            for (StationView v : List.copyOf(views.values())) {
                update(player, v, pouch, pulse);
            }
        }
    }

    private void update(Player owner, StationView view, CookiePouch pouch, boolean pulse) {
        long now = Bukkit.getCurrentTick();
        syncShelf(owner, view, pouch);
        panels.update(view, pouch, now, pulse);
        updateBasket(owner, view, pouch, now);
    }

    private boolean chunksLoaded(World world, Station station, StationVisualSettings s) {
        StationFrame frame = StationFrame.of(station);
        return chunkLoaded(world, frame, Offset.ZERO) && chunkLoaded(world, frame, cookieOffset(station, s))
                && chunkLoaded(world, panelFrame(station, PanelKind.BUILDINGS), panelOffset(station, PanelKind.BUILDINGS, s))
                && chunkLoaded(world, panelFrame(station, PanelKind.CLICKS), panelOffset(station, PanelKind.CLICKS, s));
    }

    // With a plot layout (station.shop() set) the cookie stands right on the station point and the panels on the shop
    // and clicks points: only the "up" part of the station.cookie/pedestal/panels offsets is used. Without one, the
    // offsets apply.

    private static Offset cookieOffset(Station station, StationVisualSettings s) {
        return station.shop() == null ? s.cookie().offset() : upOnly(s.cookie().offset());
    }

    private static Offset panelOffset(Station station, PanelKind kind, StationVisualSettings s) {
        Offset o = (kind == PanelKind.BUILDINGS ? s.buildings() : s.clicks()).offset();
        return anchor(station, kind) == null ? o : upOnly(o);
    }

    /**
     * Where a panel stands with a plot layout: buildings on the shop spot, click upgrades on the clicks spot. A plot
     * set up before the clicks spot existed mirrors the shop spot across the pedestal. Null without a layout.
     */
    private static Station.@Nullable Anchor anchor(Station station, PanelKind kind) {
        Station.Anchor shop = station.shop();
        if (kind == PanelKind.BUILDINGS) {
            return shop;
        }
        if (station.clicks() != null) {
            return station.clicks();
        }
        return mirroredClicks(station);
    }

    /**
     * The clicks spot of a plot set up before it existed: the shop spot mirrored across the pedestal
     * (2·station − shop on x and z, same height and yaw). Null when the station has no shop spot.
     */
    static Station.@Nullable Anchor mirroredClicks(Station station) {
        Station.Anchor shop = station.shop();
        return shop == null ? null
                : new Station.Anchor(2 * station.x() - shop.x(), shop.y(), 2 * station.z() - shop.z(), shop.yaw());
    }

    private static StationFrame panelFrame(Station station, PanelKind kind) {
        Station.Anchor a = anchor(station, kind);
        return a == null ? StationFrame.of(station) : new StationFrame(a.x(), a.y(), a.z(), a.yaw());
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
            StationVisualSettings.Pedestal ped = s.pedestal();
            Offset pedOffset = station.shop() == null ? ped.offset() : upOnly(ped.offset());
            BasketSettings b = s.basket();
            // with our pack the pedestal is the cookie basket and the cookie floats above its current look
            BasketLook look = pack && ped.enabled() ? BasketLook.pick(pouch.pouch(), null, b) : null;
            Offset co = cookieOffset(station, s);
            if (look != null) {
                co = new Offset(co.right(), look.cookieCentre(b, pedOffset.up(), c.scale(), c.bobAmplitude()),
                        co.forward());
            }
            double[] centre = frame.point(co);
            float scale = c.scale();
            long spawnTick = Bukkit.getCurrentTick();
            ItemDisplay cookie = spawn(world, centre, frame.facingViewers(0), ItemDisplay.class, owner, spawned, d -> {
                d.setItemStack(cookieStack(pack));
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(c.viewRange());
                d.setBrightness(new Display.Brightness(c.brightness(), c.brightness()));
                if (look != null) {
                    d.setTeleportDuration(b.moveTicks()); // the cookie glides when the basket grows
                }
                d.setTransformation(new Transformation(new Vector3f(0, (float) motion.bob(spawnTick), 0),
                        new Quaternionf().rotationY((float) motion.angle(spawnTick)),
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

            double[] pedestalBottom = frame.point(pedOffset);
            Display pedestal = look != null
                    ? spawnBasket(world, pedestalBottom, frame.facingViewers(0), look, owner, spawned)
                    : spawnPedestal(world, frame, new StationVisualSettings.Pedestal(ped.enabled(), ped.block(),
                            pedOffset, ped.width(), ped.height()), owner, spawned);

            int buildingRows = Math.min(s.buildings().maxRows(), economy.config().buildings().size());
            int clickRows = Math.min(s.clicks().maxRows(), economy.config().upgrades().size());
            StationView.Panel buildings = spawnPanel(PanelKind.BUILDINGS, world, station, s, owner, spawned, buildingRows);
            StationView.Panel clicks = spawnPanel(PanelKind.CLICKS, world, station, s, owner, spawned, clickRows);

            StationView view = new StationView(owner.getUniqueId(), station, cookie, cookieBox, centre, pedestal,
                    buildings, clicks, pack);
            view.motionTarget = spawnTick;
            view.pedestalBottom = pedestalBottom;
            view.pedestalUp = pedOffset.up();
            view.basketLook = look;
            register(view);
            update(owner, view, pouch, false);
            return view;
        } catch (RuntimeException e) {
            spawned.forEach(Entity::remove);
            warnOnce("spawn", "Could not spawn the bakery view at " + station.id() + ": " + e);
            return null;
        }
    }

    /**
     * One panel, laid out by {@link PanelLayout}: header, ×N toggle, {@code rowCount} row slots (icon, text) and one
     * click box over all of them.
     */
    private StationView.Panel spawnPanel(PanelKind kind, World world, Station station, StationVisualSettings s,
                                         Player owner, List<Entity> spawned, int rowCount) {
        StationVisualSettings.Panel p = s.panel();
        PanelLayout layout = PanelLayout.of(s, rowCount);
        StationFrame pf = panelFrame(station, kind);
        Offset po = panelOffset(station, kind, s);
        float textYaw = pf.facingViewers(p.textYawOffset());
        float iconYaw = pf.facingViewers(p.iconYawOffset());
        TextDisplay header = spawn(world, pf.point(po), textYaw, TextDisplay.class, owner, spawned, t -> {
            styleText(t, p, p.headerScale(), p.color("header"));
            t.setLineWidth(PanelLayout.SINGLE_LINE_WIDTH);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.text(TextPadding.centred(messages.component(kind == PanelKind.BUILDINGS ? "panel.buildings.header"
                    : "panel.clicks.header"), p.textPadding(), layout.headerTargetPx()));
        });
        TextDisplay toggle = spawn(world, pf.point(po, layout.toggleAt()), textYaw, TextDisplay.class, owner, spawned,
                t -> {
                    styleText(t, p, s.bulk().scale(), p.color("toggle"));
                    t.setLineWidth(PanelLayout.SINGLE_LINE_WIDTH);
                    t.setAlignment(TextDisplay.TextAlignment.CENTER);
                });
        float boxWidth = (float) layout.boxWidth();
        float boxHeight = (float) layout.boxHeight();
        Interaction panelBox = spawn(world, pf.point(po, layout.boxBottom()), 0, Interaction.class, owner, spawned,
                i -> {
                    i.setInteractionWidth(boxWidth);
                    i.setInteractionHeight(boxHeight);
                    i.setResponsive(true);
                });

        List<StationView.Row> rows = new ArrayList<>();
        for (int index = 0; index < rowCount; index++) {
            double[] iconAt = pf.point(po, layout.iconAt(index));
            ItemDisplay icon = spawn(world, iconAt, iconYaw, ItemDisplay.class, owner, spawned, d -> {
                d.setItemStack(ItemStack.empty());
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(p.viewRange());
                float is = p.iconScale();
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(is, is, is), new Quaternionf()));
            });
            TextDisplay text = spawn(world, pf.point(po, layout.textAt(index)), textYaw, TextDisplay.class, owner,
                    spawned, t -> {
                        styleText(t, p, p.textScale(), 0);
                        t.setAlignment(TextDisplay.TextAlignment.LEFT);
                    });
            double[] rowCentre = pf.point(po, new Offset(0, layout.rowBottom(index) + layout.rowHeight() / 2,
                    p.textOffset().forward()));
            rows.add(new StationView.Row(index, icon, text, rowCentre, iconAt));
        }
        return new StationView.Panel(kind, pf, po, iconYaw, header, toggle, panelBox, rows);
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

    private ItemDisplay spawnBasket(World world, double[] at, float yaw, BasketLook look, Player owner,
                                    List<Entity> spawned) {
        float viewRange = settings.get().cookie().viewRange();
        // origin = bottom centre of the model (contract), all sides identical
        return spawn(world, at, yaw, ItemDisplay.class, owner, spawned, d -> {
            d.setItemStack(basketStack(look));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(viewRange);
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
        String id = view.station.id();
        targets.put(view.cookieHitbox.getUniqueId(), ClickTarget.cookie(view.owner, id));
        for (StationView.Panel panel : view.panels()) {
            targets.put(panel.hitbox.getUniqueId(), ClickTarget.panel(view.owner, id, panel.kind));
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

    /** {@code cookieclicker:cookie_basket} with {@code custom_model_data} strings[0] = {@code <tier>_<fill>}. */
    private static ItemStack basketStack(BasketLook look) {
        ItemStack stack = ItemStack.of(Material.COOKIE);
        stack.setData(DataComponentTypes.ITEM_MODEL, BASKET_MODEL);
        stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData().addString(look.id()).build());
        return stack;
    }

    /** Row / shelf icon: {@code cookieclicker:building/<id>} or {@code cookieclicker:upgrade/<id>} on the fallback. */
    private ItemStack iconStack(PanelKind kind, String id, boolean withPack) {
        StationVisualSettings s = settings.get();
        boolean building = kind == PanelKind.BUILDINGS;
        String name = building ? s.panel().fallbackFor(id) : s.upgradeFallbackFor(id);
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) {
            warnOnce("icon:" + kind + ":" + id, (building ? "config.yml: station.panel.icons." + id + ".fallback-material"
                    : "config.yml: station.upgrade-fallback-items." + id) + " '" + name + "' is not an item; using COOKIE");
            material = Material.COOKIE;
        }
        ItemStack stack = ItemStack.of(material);
        if (withPack) {
            stack.setData(DataComponentTypes.ITEM_MODEL,
                    Key.key("cookieclicker", (building ? "building/" : "upgrade/") + id));
        }
        return stack;
    }

    // ---- upgrade shelf --------------------------------------------------------------------------------------

    /** World position of shelf slot {@code slot} (under the click upgrades panel). */
    private double[] slotAt(StationView view, int slot) {
        StationView.Panel clicks = view.clicks;
        return clicks.frame.point(clicks.anchor, settings.get().shelf().slot(slot));
    }

    /**
     * Makes the shelf match the bought one-time upgrades: icons for upgrades no longer shown are removed, missing
     * ones appear in place (no flight: a fresh view, or bought elsewhere), and icons whose slot changed glide there.
     */
    private void syncShelf(Player owner, StationView view, CookiePouch pouch) {
        StationVisualSettings.Shelf s = settings.get().shelf();
        List<String> wanted = PanelRows.shelf(economy.config(), pouch.upgradeLevelsView(), s.maxIcons());
        for (StationView.ShelfIcon icon : List.copyOf(view.shelf.values())) {
            if (!wanted.contains(icon.upgradeId)) {
                removeShelfIcon(view, icon);
            }
        }
        for (int slot = 0; slot < wanted.size(); slot++) {
            String id = wanted.get(slot);
            StationView.ShelfIcon icon = view.shelf.get(id);
            if (icon == null) {
                icon = spawnShelfIcon(owner, view, id, slot, slotAt(view, slot), s.scale());
                if (icon != null) {
                    addShelfHitbox(owner, view, icon);
                }
            } else if (icon.slot != slot) {
                moveShelfIcon(view, icon, slot);
            }
        }
    }

    /**
     * A one-time upgrade was just bought from {@code row}: its icon pops out of the row and, after a short delay,
     * glides (one client-interpolated teleport plus a scale interpolation, no per-tick teleports) to its shelf slot,
     * where it lands with the landing feedback. Nothing happens when its slot is past {@code station.shelf.max-icons}.
     */
    void launchToShelf(Player owner, StationView view, StationView.Row row, String upgradeId, CookiePouch pouch) {
        StationVisualSettings s = settings.get();
        int slot = PanelRows.shelf(economy.config(), pouch.upgradeLevelsView(), s.shelf().maxIcons()).indexOf(upgradeId);
        if (slot < 0 || view.shelf.containsKey(upgradeId)) {
            return;
        }
        StationView.ShelfIcon icon = spawnShelfIcon(owner, view, upgradeId, slot, row.iconAt, s.panel().iconScale());
        if (icon != null) {
            long now = Bukkit.getCurrentTick();
            icon.flightStart = now + FLY_DELAY_TICKS;
            icon.landTick = icon.flightStart + s.shelf().flyTicks();
        }
    }

    private StationView.@Nullable ShelfIcon spawnShelfIcon(Player owner, StationView view, String id, int slot,
                                                           double[] at, float scale) {
        StationVisualSettings s = settings.get();
        List<Entity> spawned = new ArrayList<>(1);
        try {
            ItemDisplay d = spawn(owner.getWorld(), at, view.clicks.iconYaw, ItemDisplay.class, owner, spawned,
                    e -> {
                        e.setItemStack(iconStack(PanelKind.CLICKS, id, view.withPack));
                        e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                        e.setBillboard(Display.Billboard.FIXED);
                        e.setViewRange(s.panel().viewRange());
                        e.setBrightness(new Display.Brightness(15, 15));
                        // every move of a shelf icon (its flight, a slot change) is one interpolated teleport
                        e.setTeleportDuration(s.shelf().flyTicks());
                        e.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                                new Vector3f(scale, scale, scale), new Quaternionf()));
                    });
            StationView.ShelfIcon icon = new StationView.ShelfIcon(id, d, slot);
            view.shelf.put(id, icon);
            byEntity.put(d.getUniqueId(), view);
            return icon;
        } catch (RuntimeException e) {
            spawned.forEach(Entity::remove); // spawned but not tracked yet: don't leave it behind
            warnOnce("shelf", "Could not spawn a shelf icon: " + e);
            return null;
        }
    }

    private void addShelfHitbox(Player owner, StationView view, StationView.ShelfIcon icon) {
        float size = settings.get().shelf().hitboxSize();
        double[] at = slotAt(view, icon.slot);
        List<Entity> spawned = new ArrayList<>(1);
        try {
            Interaction box = spawn(owner.getWorld(), new double[] {at[0], at[1] - size / 2, at[2]}, 0,
                    Interaction.class, owner, spawned, i -> {
                        i.setInteractionWidth(size);
                        i.setInteractionHeight(size);
                        i.setResponsive(true);
                    });
            icon.hitbox = box;
            byEntity.put(box.getUniqueId(), view);
            targets.put(box.getUniqueId(), ClickTarget.shelf(view.owner, view.station.id(), icon.upgradeId));
        } catch (RuntimeException e) {
            spawned.forEach(Entity::remove);
            warnOnce("shelf", "Could not spawn a shelf icon hitbox: " + e);
        }
    }

    private void moveShelfIcon(StationView view, StationView.ShelfIcon icon, int slot) {
        icon.slot = slot;
        if (icon.flightStart != Long.MIN_VALUE) {
            return; // not launched yet: it takes off toward its current slot
        }
        double[] at = slotAt(view, slot);
        World world = icon.icon.getWorld();
        icon.icon.teleport(new Location(world, at[0], at[1], at[2], view.clicks.iconYaw, 0));
        if (icon.hitbox != null) {
            float size = settings.get().shelf().hitboxSize();
            icon.hitbox.teleport(new Location(world, at[0], at[1] - size / 2, at[2]));
        }
    }

    private void removeShelfIcon(StationView view, StationView.ShelfIcon icon) {
        view.shelf.remove(icon.upgradeId);
        byEntity.remove(icon.icon.getUniqueId());
        icon.icon.remove();
        if (icon.hitbox != null) {
            byEntity.remove(icon.hitbox.getUniqueId());
            targets.remove(icon.hitbox.getUniqueId());
            icon.hitbox.remove();
        }
    }

    /** Animation tick for the shelf: start due flights, land finished ones (hitbox + landing feedback). */
    private void animateShelf(StationView view, long now) {
        if (view.shelf.isEmpty()) {
            return;
        }
        StationVisualSettings.Shelf s = settings.get().shelf();
        // no copy: nothing below adds or removes shelf icons (landing only adds a hitbox and plays feedback)
        for (StationView.ShelfIcon icon : view.shelf.values()) {
            if (!icon.flying() || !icon.icon.isValid()) {
                continue;
            }
            if (icon.flightStart != Long.MIN_VALUE && now >= icon.flightStart) {
                icon.flightStart = Long.MIN_VALUE;
                double[] at = slotAt(view, icon.slot);
                icon.icon.teleport(new Location(icon.icon.getWorld(), at[0], at[1], at[2], view.clicks.iconYaw, 0));
                icon.icon.setInterpolationDelay(0);
                icon.icon.setInterpolationDuration(s.flyTicks());
                float sc = s.scale();
                icon.icon.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(sc, sc, sc), new Quaternionf()));
            } else if (icon.flightStart == Long.MIN_VALUE && now >= icon.landTick) {
                icon.landTick = Long.MIN_VALUE;
                Player owner = Bukkit.getPlayer(view.owner);
                if (owner != null) {
                    addShelfHitbox(owner, view, icon);
                    double[] at = slotAt(view, icon.slot);
                    shelfLanding.accept(owner, new Location(owner.getWorld(), at[0], at[1], at[2]));
                }
            }
        }
    }

    // ---- cookie basket --------------------------------------------------------------------------------------

    /**
     * The basket follows the pouch (only when its look changes). A higher tier first plays the tier-up effect and
     * sound and switches at the effect's peak ({@code station.basket.tier-up-switch-ticks}); a lower tier or another
     * fill switches right away. Spawn (join, reload, coming into range) shows the current look without an effect.
     */
    private void updateBasket(Player owner, StationView v, CookiePouch pouch, long now) {
        BasketLook current = v.basketLook;
        if (v.basket == null || current == null || v.pendingLook != null) {
            return;
        }
        BasketSettings b = settings.get().basket();
        BasketLook next = BasketLook.pick(pouch.pouch(), current, b);
        if (next.equals(current)) {
            return;
        }
        if (next.tier() > current.tier()) {
            double[] at = v.pedestalBottom;
            basketTierUp.accept(owner, new Location(owner.getWorld(), at[0], at[1], at[2]));
            if (b.tierUpSwitchTicks() > 0) {
                v.pendingLook = next;
                v.basketSwitchAt = now + b.tierUpSwitchTicks();
                return;
            }
        }
        showLook(v, next);
    }

    /** Shows {@code look} on the basket and glides the cookie (and moves its hitbox) to the new height. */
    private void showLook(StationView v, BasketLook look) {
        BasketLook old = v.basketLook;
        v.pendingLook = null;
        v.basketSwitchAt = Long.MIN_VALUE;
        v.basketLook = look;
        if (v.basket == null || old == null || !v.basket.isValid()) {
            return;
        }
        v.basket.setItemStack(basketStack(look));
        StationVisualSettings s = settings.get();
        StationVisualSettings.Cookie c = s.cookie();
        double dy = look.cookieCentre(s.basket(), v.pedestalUp, c.scale(), c.bobAmplitude())
                - old.cookieCentre(s.basket(), v.pedestalUp, c.scale(), c.bobAmplitude());
        if (Math.abs(dy) < 1e-4) {
            return;
        }
        v.cookieCentre[1] += dy;
        moveUp(v.cookie, dy);
        moveUp(v.cookieHitbox, dy);
    }

    private static void moveUp(Entity e, double dy) {
        if (e.isValid()) {
            Location l = e.getLocation();
            l.setY(l.getY() + dy);
            e.teleport(l);
        }
    }

    // ---- per-tick animation: cookie motion, price rolls, shelf flights --------------------------------------

    private void animationTick() {
        long now = Bukkit.getCurrentTick();
        // no copies: segments, roll steps and shelf flights never add or remove views
        for (Map<String, StationView> views : byOwner.values()) {
            for (StationView v : views.values()) {
                // a pop owns the cookie's interpolation until its last step's tick; the next segment starts the tick
                // after it, so it can never overwrite a pop step sent in the same tick
                if (v.motionFromPop ? now > v.motionTarget : now >= v.motionTarget) {
                    segment(v, now);
                }
                panels.animate(v, now);
                animateShelf(v, now);
                if (v.pendingLook != null && now >= v.basketSwitchAt) {
                    showLook(v, v.pendingLook);
                }
            }
        }
    }

    /**
     * Before a pop on {@code view}'s cookie: if a spin segment is due this tick and not sent yet, send it now so the
     * pop's {@code setTransformation} is the last one this tick and wins.
     */
    void beforePop(StationView view) {
        long now = Bukkit.getCurrentTick();
        if (!view.motionFromPop && now >= view.motionTarget) {
            segment(view, now);
        }
    }

    /**
     * The cookie's spin/bob for {@code Effects.pop}: each pop step carries the motion at its end tick, and marks the
     * cookie as owned by the pop until that tick.
     */
    PopMotion popMotion(StationView view) {
        return new CookiePopMotion(motion, Bukkit::getCurrentTick, target -> {
            view.motionTarget = target;
            view.motionFromPop = true;
        });
    }

    /** One interpolated segment toward the motion at {@code now + segment}; keeps the current scale (pop rule). */
    private void segment(StationView v, long now) {
        int seg = settings.get().cookie().segmentTicks();
        long target = now + seg;
        v.motionTarget = target;
        v.motionFromPop = false;
        if (!v.cookie.isValid()) {
            return;
        }
        Transformation current = v.cookie.getTransformation();
        v.cookie.setInterpolationDelay(0);
        v.cookie.setInterpolationDuration(seg);
        v.cookie.setTransformation(new Transformation(new Vector3f(0, (float) motion.bob(target), 0),
                new Quaternionf().rotationY((float) motion.angle(target)),
                new Vector3f(current.getScale()), new Quaternionf(current.getRightRotation())));
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
        modes.forget(event.getPlayer().getUniqueId());
        pages.forget(event.getPlayer().getUniqueId());
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
