package dev.casperrs.cookieClicker;

import dev.casperrs.cookieClicker.belt.ConveyorBelts;
import dev.casperrs.cookieClicker.building.VisibleBuildings;
import dev.casperrs.cookieClicker.command.BakeryCommand;
import dev.casperrs.cookieClicker.command.DbCommand;
import dev.casperrs.cookieClicker.command.FarmCommand;
import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.farm.FarmAmbience;
import dev.casperrs.cookieClicker.farm.FarmRegistry;
import dev.casperrs.cookieClicker.farm.FarmTeleports;
import dev.casperrs.cookieClicker.house.cinematic.HouseCinematics;
import dev.casperrs.cookieClicker.house.setup.HousePlacer;
import dev.casperrs.cookieClicker.house.cinematic.HouseSeen;
import dev.casperrs.cookieClicker.house.setup.FarmSetup;
import dev.casperrs.cookieClicker.house.setup.PlotSetup;
import dev.casperrs.cookieClicker.hud.BakeryTicker;
import dev.casperrs.cookieClicker.hud.Hud;
import dev.casperrs.cookieClicker.pack.ResourcePackSender;
import dev.casperrs.cookieClicker.pack.ResourcePackTracker;
import dev.casperrs.cookieClicker.plot.LiquidGuard;
import dev.casperrs.cookieClicker.plot.PedestalPrivacy;
import dev.casperrs.cookieClicker.plot.PlotFile;
import dev.casperrs.cookieClicker.plot.PlotOutlines;
import dev.casperrs.cookieClicker.plot.PlotRegistry;
import dev.casperrs.cookieClicker.plot.PlotService;
import dev.casperrs.cookieClicker.plot.PlotTracker;
import dev.casperrs.cookieClicker.session.SessionBukkitListener;
import dev.casperrs.cookieClicker.session.SessionListener;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.station.StationFile;
import dev.casperrs.cookieClicker.station.StationRegistry;
import dev.casperrs.cookieClicker.storage.DatabaseSettings;
import dev.casperrs.cookieClicker.storage.DriverConnectionFactory;
import dev.casperrs.cookieClicker.storage.JdbcPlayerStore;
import dev.casperrs.cookieClicker.storage.PooledConnectionFactory;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.vfx.Effects;
import dev.casperrs.cookieClicker.view.ClickRouter;
import dev.casperrs.cookieClicker.view.StationInteractions;
import dev.casperrs.cookieClicker.view.StationViews;
import dev.casperrs.cookieClicker.worldguard.WorldGuardHook;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Builds and starts every part in dependency order, re-applies them on reload, and lists their shutdown steps.
 * Fields stay null until built, so a failed enable still shuts down what exists.
 */
final class PluginWiring {

    private static final long TICKS_PER_SECOND = 20;

    /** The plugin can't run (e.g. MariaDB unreachable); the message is the one log line, without secrets. */
    static final class StartupFailure extends Exception {
        StartupFailure(String message) {
            super(message);
        }
    }

    private final CookieClicker plugin;
    private final Supplier<PluginSettings> settings;
    private final Messages messages;
    private final BakeryConfigLoader loader;

    private @Nullable PooledConnectionFactory pool;
    private @Nullable ExecutorService io;
    private @Nullable SessionManager sessions;
    private @Nullable StationRegistry stations;
    private @Nullable PlotService plots;
    private @Nullable PlotRegistry plotRegistry;
    private @Nullable Economy economy;
    private @Nullable ResourcePackTracker resourcePacks;
    private @Nullable ResourcePackSender packSender;
    private @Nullable SoundService sounds;
    private @Nullable Hud hud;
    private @Nullable BakeryTicker ticker;
    private @Nullable StationViews views;
    private @Nullable Effects effects;
    private @Nullable PlotTracker plotTracker;
    private @Nullable HouseCinematics cinematics;
    private @Nullable ConveyorBelts belts;
    private @Nullable VisibleBuildings buildings;
    private @Nullable PlotOutlines outlines;
    private @Nullable PedestalPrivacy pedestalPrivacy;
    private @Nullable HousePlacer houses;
    private @Nullable PlotSetup setup;
    private @Nullable FarmRegistry farms;
    private @Nullable FarmSetup farmSetup;
    private @Nullable FarmAmbience farmAmbience;
    private @Nullable BukkitTask tickTask;
    private @Nullable BukkitTask autosaveTask;

    PluginWiring(CookieClicker plugin, Supplier<PluginSettings> settings, Messages messages, BakeryConfigLoader loader) {
        this.plugin = plugin;
        this.settings = settings;
        this.messages = messages;
        this.loader = loader;
    }

    void build(WorldGuardHook regions, List<String> problems) throws StartupFailure {
        PluginSettings s = settings.get();
        pool = openPool(s.database()); // first: a dead MariaDB stops the enable before anything is registered
        io = PluginExecutors.io();
        Executor mainThread = PluginExecutors.main(plugin);

        economy = new Economy(EconomyConfig.DISABLED, RandomGenerator.getDefault());
        loader.loadBalance(economy, messages, problems);

        resourcePacks = new ResourcePackTracker(ResourcePackSender.PACK_ID);
        resourcePacks.setAcceptAnyPack(!s.resourcePack().enabled());
        resourcePacks.onDeclined(p -> messages.send(p, "resource-pack.declined"));
        packSender = new ResourcePackSender(plugin, () -> settings.get().resourcePack(), messages);
        sounds = new SoundService(plugin.getLogger(), RandomGenerator.getDefault(), resourcePacks::hasPack);
        loader.loadSounds(sounds, problems);

        plugin.getLogger().info("Storage: " + s.database().describe());
        JdbcPlayerStore store = new JdbcPlayerStore(pool, s.database().tablePrefix());
        sessions = new SessionManager(store, io, mainThread, plugin.getLogger());
        sessions.addListener(new SessionListener() {
            @Override
            public void onLoadFailed(UUID player) {
                Player p = Bukkit.getPlayer(player);
                if (p != null) {
                    messages.send(p, "storage.load-failed");
                }
            }
        });
        sessions.start();
        HouseSeen houseSeen = new HouseSeen(store, io, mainThread, plugin.getLogger());

        stations = new StationRegistry(plugin.getLogger());
        plotRegistry = new PlotRegistry(regions, PlotService::worldByKey);
        PlotFile plotFile = new PlotFile(plugin.getDataFolder().toPath(), plugin.getLogger(), io);
        plots = new PlotService(plotRegistry, plotFile, new StationFile(plugin, io), stations,
                plugin.getDataFolder().toPath(), plugin.getLogger(), PlotService::worldByKey);
        plots.load(loader.collector(problems));
        farms = new FarmRegistry(plotFile, plotRegistry::all);
        farms.load(loader.collector(problems));

        hud = new Hud(messages, economy, resourcePacks::hasPack);
        ticker = new BakeryTicker(sessions, economy, hud, Bukkit::getPlayer);
        applySettings();

        views = new StationViews(plugin, () -> settings.get().visuals(), stations, sessions, economy, messages,
                resourcePacks::hasPack);
        ticker.setAfterProduce(views::refreshPanels); // rows light up the second they become affordable
        views.sweep(true); // crash leftovers of every tagged entity, before the vfx engine starts

        effects = new Effects(plugin, Messages.miniMessage());
        effects.setPackCheck(resourcePacks::hasPack);
        effects.load();

        PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(resourcePacks, plugin);
        pm.registerEvents(packSender, plugin);
        pm.registerEvents(new SessionBukkitListener(sessions), plugin);
        StationInteractions interactions = new StationInteractions(views, sessions, economy, effects, sounds, hud,
                messages);
        views.setShelfLanding(interactions::iconShelved);
        views.setBasketTierUp(interactions::basketTierUp);
        pm.registerEvents(new ClickRouter(views, interactions, () -> settings.get().visuals(), plugin.getLogger()),
                plugin);
        views.start();
        pm.registerEvents(new LiquidGuard(plotRegistry), plugin);

        plotTracker = new PlotTracker(plugin, plotRegistry);
        cinematics = new HouseCinematics(plugin, plotRegistry, plotTracker, houseSeen, effects, sounds, messages,
                () -> settings.get().house());
        cinematics.start(); // before the tracker: its first resync fires onEnterPlot for players already online
        plotTracker.start();
        belts = new ConveyorBelts(plugin, plotRegistry, plotTracker, sessions, economy, effects, sounds,
                resourcePacks::hasPack, cinematics::isRunning, () -> settings.get().belt(),
                () -> settings.get().house().ambient().radius());
        belts.start();
        buildings = new VisibleBuildings(plugin, plotRegistry, plotTracker, sessions, effects, sounds,
                resourcePacks::hasPack, () -> settings.get().plot().buildings());
        interactions.setBuildingsBought(buildings::bought);
        buildings.start(); // shows players already in a plot (quietly)
        outlines = new PlotOutlines(plugin, effects, () -> settings.get().plot().show());
        pedestalPrivacy = new PedestalPrivacy(plugin, () -> settings.get().plot().hideOthers(), stations::all);
        pedestalPrivacy.restart();
        houses = new HousePlacer(plugin, plotRegistry, plotFile, messages, io, mainThread,
                () -> settings.get().house().maxBlocks(), cinematics::plotChanged);
        setup = new PlotSetup(plugin, plotRegistry,
                plot -> {
                    plots.update(plot, problem -> plugin.getLogger().warning(problem));
                    cinematics.plotChanged(plot.id());
                    belts.plotChanged(plot.id());
                    buildings.plotChanged(plot.id());
                }, messages, io, mainThread,
                () -> settings.get().house().maxBlocks(), () -> settings.get().plot().buildings().footprints());

        farmSetup = new FarmSetup(plugin, regions, farms, messages, io, mainThread,
                () -> settings.get().farm().pasteBlocksPerTick());
        farmAmbience = new FarmAmbience(plugin, effects, farms, () -> settings.get().house().chimney().smoke());
        farms.addListener(farmAmbience::restart); // reload: loops killed by effects.reload come back on the next check
        farmAmbience.restart();
        FarmTeleports teleports = new FarmTeleports(farms, plotRegistry, messages,
                () -> settings.get().farm().teleportCooldownSeconds());
        pm.registerEvents(teleports, plugin);
        plotTracker.addListener(teleports);
        FarmCommand farmCommand = new FarmCommand(farms, farmSetup, teleports, outlines, regions,
                () -> settings.get().plot().show(), messages);

        DbCommand db = new DbCommand(settings, sessions, pool, messages, plugin.getLogger());
        BakeryCommand command = new BakeryCommand(plots, outlines, houses, setup, cinematics, settings, messages,
                plugin::reload, db, farmCommand);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(command.node(), "Cookie Empire admin commands"));

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, ticker, TICKS_PER_SECOND, TICKS_PER_SECOND);
        Bukkit.getOnlinePlayers().forEach(p -> sessions.join(p.getUniqueId())); // e.g. after /reload confirm

        plugin.getLogger().info("Enabled with " + plotRegistry.all().size() + " plot(s), " + stations.all().size()
                + " pedestal(s), " + farms.all().size() + " farm(s), " + economy.config().buildings().size()
                + " building(s)"
                + (problems.isEmpty() ? "" : "; " + problems.size() + " config problem(s), see above"));
    }

    /**
     * The connection pool. MariaDB is probed once here (blocking, at enable only): unreachable or a refused login
     * fails the enable, and there is never a fallback to H2. H2 keeps its old behaviour (errors surface at the
     * schema init on the storage thread).
     */
    private static PooledConnectionFactory openPool(DatabaseSettings db) throws StartupFailure {
        DriverConnectionFactory raw = new DriverConnectionFactory(db);
        if (db.type() == DatabaseSettings.Type.MARIADB) {
            try {
                PooledConnectionFactory.probe(raw);
            } catch (SQLException e) {
                throw new StartupFailure("Could not connect to " + db.describe() + ": " + e.getMessage()
                        + ". Is MariaDB running, and are database.mariadb.* in config.yml right? CookieClicker is"
                        + " disabled; it never falls back to H2.");
            }
        }
        return new PooledConnectionFactory(db, raw);
    }

    /** Everything after the new settings are in place; the order matters (see the inline notes). */
    void reload(PluginSettings.ResourcePack packBefore, List<String> problems) {
        resourcePacks.setAcceptAnyPack(!settings.get().resourcePack().enabled());
        loader.loadMessages(messages, problems);
        loader.loadBalance(economy, messages, problems);
        loader.loadSounds(sounds, problems);
        outlines.stopAll();
        cinematics.abortAll(); // restores hidden houses (and camera game modes) before effects and regions change
        belts.restart();
        houses.reset();
        setup.reset();
        farmSetup.reset();
        plots.load(loader.collector(problems));
        farms.load(loader.collector(problems));
        effects.reload();
        views.restart(); // removes every view; the next refresh respawns with the new layout and balance
        plotTracker.resyncAll();
        buildings.reload(); // after the resync: rebuilt quietly for everyone in a plot
        cinematics.restart(); // smoke loops died with effects.reload()
        pedestalPrivacy.restart();
        applySettings();
        packSender.onReload(packBefore);
    }

    private void applySettings() {
        PluginSettings s = settings.get();
        hud.setEnabled(s.hud().enabled());
        ticker.setHudEverySeconds(s.hud().refreshSeconds());
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long period = s.autosaveSeconds() * TICKS_PER_SECOND;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin, sessions::saveAll, period, period);
    }

    /**
     * Shutdown steps in order: tasks, the cinematics first (they restore hidden houses and game modes), world parts,
     * views, effects, stations, the session flush (which also stops the IO thread), and last the connection pool.
     */
    List<PluginPart> shutdownOrder() {
        List<PluginPart> parts = new ArrayList<>();
        add(parts, "the tick task", tickTask, BukkitTask::cancel);
        add(parts, "the autosave task", autosaveTask, BukkitTask::cancel);
        add(parts, "the house cinematics", cinematics, HouseCinematics::shutdown);
        add(parts, "the conveyor belts", belts, ConveyorBelts::shutdown);
        add(parts, "the visible buildings", buildings, VisibleBuildings::shutdown);
        add(parts, "plot tracking", plotTracker, PlotTracker::shutdown);
        add(parts, "the plot outlines", outlines, PlotOutlines::shutdown);
        add(parts, "pedestal privacy", pedestalPrivacy, PedestalPrivacy::shutdown); // shows every player it hid
        add(parts, "house placing", houses, HousePlacer::reset);
        add(parts, "plot setup", setup, PlotSetup::reset);
        add(parts, "farm setup", farmSetup, FarmSetup::reset);
        add(parts, "the farm smoke", farmAmbience, FarmAmbience::stopAll);
        add(parts, "the station views", views, StationViews::shutdown);
        add(parts, "the effects", effects, Effects::shutdown);
        add(parts, "the stations", stations, StationRegistry::clear); // listeners despawn; plots.yml is untouched
        if (sessions != null) {
            SessionManager s = sessions;
            parts.add(new PluginPart("the sessions", () -> s.shutdown(settings.get().shutdownSaveTimeoutSeconds())));
        } else if (io != null) {
            parts.add(new PluginPart("the IO thread", io::shutdown));
        }
        add(parts, "the database pool", pool, PooledConnectionFactory::close); // after the final saves
        return parts;
    }

    private static <T> void add(List<PluginPart> parts, String name, @Nullable T part,
                                Consumer<T> step) {
        if (part != null) {
            parts.add(new PluginPart(name, () -> step.accept(part)));
        }
    }
}
