package dev.casperrs.cookieClicker;

import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.worldguard.WorldGuardHook;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Cookie Empire. Owns the lifecycle (load, enable, reload, disable); {@link PluginWiring} builds the parts and
 * feature logic lives in the sub-packages.
 */
public final class CookieClicker extends JavaPlugin {

    private PluginSettings settings;
    private BakeryConfigLoader loader;
    private @Nullable PluginWiring wiring;
    private boolean flagsRegistered;

    /** WorldGuard flags can only be registered before WorldGuard enables. */
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
        List<String> problems = new ArrayList<>();
        Messages messages = new Messages(getLogger());
        loader.loadMessages(messages, problems);

        WorldGuardHook regions = worldGuard();
        if (regions == null) {
            getLogger().severe(PlainTextComponentSerializer.plainText().serialize(
                    messages.component("plot.missing-worldguard")));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        settings = loader.loadSettings(problems);
        wiring = new PluginWiring(this, () -> settings, messages, loader);
        wiring.build(regions, problems);
    }

    /** The region hook, or null when WorldGuard is missing, disabled or our flags could not be registered. */
    private @Nullable WorldGuardHook worldGuard() {
        Plugin wg = getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled() || !flagsRegistered) {
            return null;
        }
        String version = wg.getPluginMeta().getVersion();
        if (WorldGuardHook.olderThan(version, WorldGuardHook.MIN_VERSION)) {
            getLogger().warning("WorldGuard " + version + " is older than " + WorldGuardHook.MIN_VERSION
                    + ", the first release confirmed for Paper 26.3; update it if plots misbehave");
        }
        return WorldGuardHook.create(getLogger()).orElse(null);
    }

    /** Every step runs even when an earlier one throws, so the session flush at the end is never skipped. */
    @Override
    public void onDisable() {
        if (wiring == null) {
            return;
        }
        for (PluginPart part : wiring.shutdownOrder()) {
            try {
                part.shutdown().run();
            } catch (RuntimeException e) {
                getLogger().log(Level.SEVERE, "Could not shut down " + part.name() + " on disable", e);
            }
        }
    }

    /**
     * {@code /bakery reload}: config, messages, sounds, effects, balance, plots (re-reads plots.yml and re-resolves
     * the WorldGuard regions, so {@code /rg redefine} applies) and stations. The database needs a restart.
     */
    public List<String> reload() {
        List<String> problems = new ArrayList<>();
        PluginSettings fresh = loader.loadSettings(problems);
        if (!fresh.database().equals(settings.database())) {
            String note = "database.* changed; restart the server to switch databases";
            getLogger().warning(note);
            problems.add(note);
        }
        PluginSettings.ResourcePack packBefore = settings.resourcePack();
        settings = fresh.withDatabase(settings.database());
        wiring.reload(packBefore, problems);
        return problems;
    }
}
