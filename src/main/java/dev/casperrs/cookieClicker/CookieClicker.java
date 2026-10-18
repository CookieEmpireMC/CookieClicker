package dev.casperrs.cookieClicker;

import dev.casperrs.cookieClicker.command.BakeryCommand;
import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.hud.BakeryTicker;
import dev.casperrs.cookieClicker.hud.Hud;
import dev.casperrs.cookieClicker.pack.ResourcePackTracker;
import dev.casperrs.cookieClicker.session.SessionBukkitListener;
import dev.casperrs.cookieClicker.session.SessionListener;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.station.StationFile;
import dev.casperrs.cookieClicker.station.StationRegistry;
import dev.casperrs.cookieClicker.storage.DriverConnectionFactory;
import dev.casperrs.cookieClicker.storage.JdbcPlayerStore;
import dev.casperrs.cookieClicker.text.Messages;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.random.RandomGenerator;

/**
 * Cookie Empire. This class only wires services together and owns their lifecycle
 * (enable → reload → disable); feature logic lives in the sub-packages.
 *
 * <p>Pass 2 plugs in through the getters: {@link #stations()} (add a {@code StationListener} for the
 * station visuals), {@link #sessions()} (a {@link SessionListener} for per-player UI), {@link #economy()},
 * {@link #sounds()}, {@link #hud()}, {@link #messages()}, {@link #resourcePacks()} and {@link #settings()}.
 */
public final class CookieClicker extends JavaPlugin {

    private static final long TICKS_PER_SECOND = 20;

    private PluginSettings settings;
    private Messages messages;
    private Economy economy;
    private SoundService sounds;
    private ResourcePackTracker resourcePacks;
    private StationRegistry stations;
    private StationFile stationFile;
    private SessionManager sessions;
    private Hud hud;
    private BakeryTicker ticker;
    private ExecutorService io;
    private BakeryConfigLoader loader;
    private BukkitTask tickTask;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        loader = new BakeryConfigLoader(this);
        List<String> problems = BakeryConfigLoader.newProblemList();
        settings = loader.loadSettings(problems);

        // One storage thread: DB loads/saves and stations.yml writes, in order
        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CookieClicker-IO");
            t.setDaemon(true);
            return t;
        });
        Executor mainThread = task -> {
            if (isEnabled()) {
                Bukkit.getScheduler().runTask(this, task);
            }
        };

        messages = new Messages(getLogger());
        loader.loadMessages(messages, problems);

        economy = new Economy(EconomyConfig.DISABLED, RandomGenerator.getDefault());
        loader.loadBalance(economy, messages, problems);

        resourcePacks = new ResourcePackTracker();
        sounds = new SoundService(getLogger(), RandomGenerator.getDefault(), resourcePacks::hasPack);
        loader.loadSounds(sounds, problems);

        getLogger().info("Storage: " + settings.database().describe());
        sessions = new SessionManager(new JdbcPlayerStore(new DriverConnectionFactory(settings.database()),
                settings.database().tablePrefix()), io, mainThread, getLogger());
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

        stations = new StationRegistry(getLogger());
        stationFile = new StationFile(this, io);
        loader.loadStations(stationFile, stations, problems);

        hud = new Hud(messages, economy, resourcePacks::hasPack);
        ticker = new BakeryTicker(sessions, economy, hud, Bukkit::getPlayer);
        applySettings();

        var pm = getServer().getPluginManager();
        pm.registerEvents(resourcePacks, this);
        pm.registerEvents(new SessionBukkitListener(sessions), this);

        BakeryCommand command = new BakeryCommand(stations, stationFile, () -> settings, messages, this::reload);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(command.node(), "Cookie Empire admin commands"));

        tickTask = Bukkit.getScheduler().runTaskTimer(this, ticker, TICKS_PER_SECOND, TICKS_PER_SECOND);

        // Players already online (e.g. after /reload confirm) get their sessions too
        Bukkit.getOnlinePlayers().forEach(p -> sessions.join(p.getUniqueId()));

        getLogger().info("Enabled with " + stations.all().size() + " station(s), "
                + economy.config().buildings().size() + " building(s)"
                + (problems.isEmpty() ? "" : "; " + problems.size() + " config problem(s), see above"));
    }

    @Override
    public void onDisable() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (stations != null) {
            stations.clear(); // tells pass-2 listeners to despawn everything; stations.yml is untouched
        }
        if (sessions != null) {
            sessions.shutdown(settings.shutdownSaveTimeoutSeconds()); // flushes every save, then stops the IO thread
        } else if (io != null) {
            io.shutdown();
        }
    }

    /** {@code /bakery reload}: config, messages, sounds, balance and stations. The database needs a restart. */
    public List<String> reload() {
        List<String> problems = BakeryConfigLoader.newProblemList();
        PluginSettings fresh = loader.loadSettings(problems);
        if (!fresh.database().equals(settings.database())) {
            String note = "database.* changed; restart the server to switch databases";
            getLogger().warning(note);
            problems.add(note);
        }
        settings = new PluginSettings(settings.database(), fresh.autosaveSeconds(), fresh.shutdownSaveTimeoutSeconds(),
                fresh.hud(), fresh.station(), fresh.resourcePack());
        loader.loadMessages(messages, problems);
        loader.loadBalance(economy, messages, problems);
        loader.loadSounds(sounds, problems);
        loader.loadStations(stationFile, stations, problems);
        applySettings();
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

    public SessionManager sessions() {
        return sessions;
    }

    public Hud hud() {
        return hud;
    }
}
