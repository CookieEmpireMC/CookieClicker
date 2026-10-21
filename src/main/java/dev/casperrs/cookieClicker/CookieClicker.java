package dev.casperrs.cookieClicker;

import dev.casperrs.cookieClicker.belt.ConveyorBelts;
import dev.casperrs.cookieClicker.command.BakeryCommand;
import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.house.HouseCinematics;
import dev.casperrs.cookieClicker.house.PlotSetup;
import dev.casperrs.cookieClicker.house.HousePlacer;
import dev.casperrs.cookieClicker.house.HouseSeen;
import dev.casperrs.cookieClicker.hud.BakeryTicker;
import dev.casperrs.cookieClicker.hud.Hud;
import dev.casperrs.cookieClicker.pack.ResourcePackSender;
import dev.casperrs.cookieClicker.pack.ResourcePackTracker;
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
import dev.casperrs.cookieClicker.storage.DriverConnectionFactory;
import dev.casperrs.cookieClicker.storage.JdbcPlayerStore;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.vfx.Effects;
import dev.casperrs.cookieClicker.view.ClickRouter;
import dev.casperrs.cookieClicker.view.StationInteractions;
import dev.casperrs.cookieClicker.view.StationViews;
import dev.casperrs.cookieClicker.worldguard.WorldGuardHook;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.random.RandomGenerator;

/**
 * Cookie Empire. This class only wires services together and owns their lifecycle
 * (load → enable → reload → disable); feature logic lives in the sub-packages.
 *
 * <p>Load: WorldGuard flags {@code bakery-plot} / {@code bakery-house} (only possible before WorldGuard enables).
 * <p>Disable order: tasks -> plot tracker, outlines, pedestal privacy -> station views -> effects -> stations ->
 * sessions (flush saves, stop IO).
 */
public final class CookieClicker extends JavaPlugin {

    private static final long TICKS_PER_SECOND = 20;

    private PluginSettings settings;
    private Messages messages;
    private Economy economy;
    private SoundService sounds;
    private ResourcePackTracker resourcePacks;
    private ResourcePackSender packSender;
    private Effects effects;
    private StationViews views;
    private StationRegistry stations;
    private PlotService plots;
    private PlotTracker plotTracker;
    private PlotOutlines outlines;
    private PedestalPrivacy pedestalPrivacy;
    private HousePlacer houses;
    private PlotSetup setup;
    private HouseSeen houseSeen;
    private HouseCinematics cinematics;
    private ConveyorBelts belts;
    private SessionManager sessions;
    private Hud hud;
    private BakeryTicker ticker;
    private ExecutorService io;
    private BakeryConfigLoader loader;
    private BukkitTask tickTask;
    private BukkitTask autosaveTask;
    private boolean flagsRegistered;

    @Override
    public void onLoad() {
        try {
            WorldGuardHook.registerFlags();
            flagsRegistered = true;
        } catch (RuntimeException | LinkageError e) { // conflict, or WorldGuard not usable
            getLogger().log(Level.SEVERE, "Could not register the WorldGuard flags bakery-plot / bakery-house", e);
        }
    }

    @Override
    public void onEnable() {
        loader = new BakeryConfigLoader(this);
        List<String> problems = BakeryConfigLoader.newProblemList();
        messages = new Messages(getLogger());
        loader.loadMessages(messages, problems);

        WorldGuardHook regions = worldGuard();
        if (regions == null) {
            getLogger().severe(PlainTextComponentSerializer.plainText().serialize(
                    messages.component("plot.missing-worldguard")));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        settings = loader.loadSettings(problems);

        // One storage thread: DB loads/saves, plots.yml/stations.yml writes and house backups, in order
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CookieClicker-IO");
            t.setDaemon(true);
            return t;
        });
        Executor mainThread = task -> {
            if (!isEnabled()) {
                return; // shutting down: sessions are already flushed
            }
            try {
                Bukkit.getScheduler().runTask(this, task);
            } catch (IllegalPluginAccessException e) { // disabled in the meantime
                getLogger().fine("Dropped a storage callback during shutdown");
            }
        };

        economy = new Economy(EconomyConfig.DISABLED, RandomGenerator.getDefault());
        loader.loadBalance(economy, messages, problems);

        resourcePacks = new ResourcePackTracker(ResourcePackSender.PACK_ID);
        resourcePacks.setAcceptAnyPack(!settings.resourcePack().enabled());
        resourcePacks.onDeclined(p -> messages.send(p, "resource-pack.declined"));
        packSender = new ResourcePackSender(this, () -> settings.resourcePack(), messages);
        sounds = new SoundService(getLogger(), RandomGenerator.getDefault(), resourcePacks::hasPack);
        loader.loadSounds(sounds, problems);

        getLogger().info("Storage: " + settings.database().describe());
        JdbcPlayerStore store = new JdbcPlayerStore(new DriverConnectionFactory(settings.database()),
                settings.database().tablePrefix());
        sessions = new SessionManager(store, io, mainThread, getLogger());
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
        houseSeen = new HouseSeen(store, io, mainThread, getLogger());

        stations = new StationRegistry(getLogger());
        PlotRegistry plotRegistry = new PlotRegistry(regions, PlotService::worldByKey);
        PlotFile plotFile = new PlotFile(getDataFolder().toPath(), getLogger(), io);
        plots = new PlotService(plotRegistry, plotFile, new StationFile(this, io), stations,
                getDataFolder().toPath(), getLogger(), PlotService::worldByKey);
        loader.loadPlots(plots, problems);

        hud = new Hud(messages, economy, resourcePacks::hasPack);
        ticker = new BakeryTicker(sessions, economy, hud, Bukkit::getPlayer);
        applySettings();

        views = new StationViews(this, () -> settings.visuals(), stations, sessions, economy, messages,
                resourcePacks::hasPack);
        ticker.setAfterProduce(views::refreshPanels); // rows light up the second they become affordable
        views.sweep(true); // crash leftovers of every tagged entity, before the vfx engine starts

        effects = new Effects(this, Messages.miniMessage());
        effects.setPackCheck(resourcePacks::hasPack);
        effects.load();

        var pm = getServer().getPluginManager();
        pm.registerEvents(resourcePacks, this);
        pm.registerEvents(packSender, this);
        pm.registerEvents(new SessionBukkitListener(sessions), this);
        StationInteractions interactions = new StationInteractions(views, sessions, economy, effects, sounds, hud, messages);
        pm.registerEvents(new ClickRouter(views, interactions, () -> settings.visuals(), getLogger()), this);
        views.start();

        plotTracker = new PlotTracker(this, plotRegistry);
        cinematics = new HouseCinematics(this, plotRegistry, plotTracker, houseSeen, effects, sounds, messages,
                () -> settings.house());
        cinematics.start(); // before the tracker: its first resync fires onEnterPlot for players already online
        plotTracker.start();
        belts = new ConveyorBelts(this, plotRegistry, plotTracker, sessions, economy, effects, sounds,
                resourcePacks::hasPack, cinematics::isRunning, () -> settings.belt(),
                () -> settings.house().ambient().radius());
        belts.start();
        outlines = new PlotOutlines(this, effects, () -> settings.plot().show());
        pedestalPrivacy = new PedestalPrivacy(this, () -> settings.plot().hideOthers(), stations::all);
        pedestalPrivacy.restart();
        houses = new HousePlacer(this, plotRegistry, plotFile, messages, io, mainThread,
                () -> settings.house().maxBlocks());
        setup = new PlotSetup(this, plotRegistry,
                plot -> {
                    plots.update(plot, problem -> getLogger().warning(problem));
                    cinematics.plotChanged(plot.id());
                    belts.plotChanged(plot.id());
                }, messages, io, mainThread,
                () -> settings.house().maxBlocks());

        BakeryCommand command = new BakeryCommand(plots, outlines, houses, setup, cinematics, () -> settings, messages, this::reload);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(command.node(), "Cookie Empire admin commands"));

        tickTask = Bukkit.getScheduler().runTaskTimer(this, ticker, TICKS_PER_SECOND, TICKS_PER_SECOND);

        // Players already online (e.g. after /reload confirm) get their sessions too
        Bukkit.getOnlinePlayers().forEach(p -> sessions.join(p.getUniqueId()));

        getLogger().info("Enabled with " + plotRegistry.all().size() + " plot(s), " + stations.all().size()
                + " pedestal(s), " + economy.config().buildings().size() + " building(s)"
                + (problems.isEmpty() ? "" : "; " + problems.size() + " config problem(s), see above"));
    }

    /** The region hook, or null when WorldGuard is missing, disabled or our flags could not be registered. */
    private WorldGuardHook worldGuard() {
        Plugin wg = getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled() || !flagsRegistered) {
            return null;
        }
        String version = wg.getPluginMeta().getVersion();
        if (olderThan(version, WorldGuardHook.MIN_VERSION)) {
            getLogger().warning("WorldGuard " + version + " is older than " + WorldGuardHook.MIN_VERSION
                    + ", the first release confirmed for Paper 26.3; update it if plots misbehave");
        }
        return WorldGuardHook.create(getLogger()).orElse(null);
    }

    /** Compares the leading dotted numbers ("7.0.20-SNAPSHOT;abc" -> 7, 0, 20). Unparseable = not older. */
    static boolean olderThan(String version, String min) {
        String[] a = version.split("[^0-9.]", 2)[0].split("\\.");
        String[] b = min.split("\\.");
        for (int i = 0; i < b.length; i++) {
            int x;
            try {
                x = i < a.length ? Integer.parseInt(a[i]) : 0;
            } catch (NumberFormatException e) {
                return false;
            }
            int y = Integer.parseInt(b[i]);
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        // a throw here must never skip the session flush below
        if (cinematics != null) {
            safely("the house cinematics", cinematics::shutdown); // restores every hidden house and game mode first
        }
        if (belts != null) {
            safely("the conveyor belts", belts::shutdown);
        }
        safely("plot tracking", () -> {
            if (plotTracker != null) {
                plotTracker.shutdown();
            }
            if (outlines != null) {
                outlines.shutdown();
            }
            if (pedestalPrivacy != null) {
                pedestalPrivacy.shutdown(); // shows every player it hid
            }
            if (houses != null) {
                houses.reset();
            }
            if (setup != null) {
                setup.reset();
            }
        });
        if (views != null) {
            safely("the station views", views::shutdown); // every player's UI, independent of session events
        }
        if (effects != null) {
            safely("the effects", effects::shutdown);
        }
        if (stations != null) {
            stations.clear(); // tells listeners to despawn everything; plots.yml is untouched
        }
        if (sessions != null) {
            sessions.shutdown(settings.shutdownSaveTimeoutSeconds()); // flushes every save, then stops the IO thread
        } else if (io != null) {
            io.shutdown();
        }
    }

    private void safely(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Could not shut down " + what + " on disable", e);
        }
    }

    /**
     * {@code /bakery reload}: config, messages, sounds, effects, balance, plots (re-reads plots.yml and re-resolves
     * the WorldGuard regions, so {@code /rg redefine} applies) and stations. The database needs a restart.
     */
    public List<String> reload() {
        List<String> problems = BakeryConfigLoader.newProblemList();
        PluginSettings fresh = loader.loadSettings(problems);
        if (!fresh.database().equals(settings.database())) {
            String note = "database.* changed; restart the server to switch databases";
            getLogger().warning(note);
            problems.add(note);
        }
        PluginSettings.ResourcePack packBefore = settings.resourcePack();
        settings = fresh.withDatabase(settings.database());
        resourcePacks.setAcceptAnyPack(!settings.resourcePack().enabled());
        loader.loadMessages(messages, problems);
        loader.loadBalance(economy, messages, problems);
        loader.loadSounds(sounds, problems);
        outlines.stopAll();
        cinematics.abortAll(); // restores hidden houses (and camera game modes) before effects and regions change
        belts.restart();
        houses.reset();
        setup.reset();
        loader.loadPlots(plots, problems);
        effects.reload();
        views.restart(); // removes every view; the next refresh respawns with the new layout and balance
        plotTracker.resyncAll();
        cinematics.restart(); // smoke loops died with effects.reload()
        pedestalPrivacy.restart();
        applySettings();
        packSender.onReload(packBefore);
        return problems;
    }

    private void applySettings() {
        hud.setEnabled(settings.hud().enabled());
        ticker.setHudEverySeconds(settings.hud().refreshSeconds());
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long period = settings.autosaveSeconds() * TICKS_PER_SECOND;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, sessions::saveAll, period, period);
    }

    public PluginSettings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Economy economy() {
        return economy;
    }

    public SoundService sounds() {
        return sounds;
    }

    public ResourcePackTracker resourcePacks() {
        return resourcePacks;
    }

    public StationRegistry stations() {
        return stations;
    }

    public PlotService plots() {
        return plots;
    }

    public PlotTracker plotTracker() {
        return plotTracker;
    }

    public HouseSeen houseSeen() {
        return houseSeen;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public Hud hud() {
        return hud;
    }

    public Effects effects() {
        return effects;
    }
}
