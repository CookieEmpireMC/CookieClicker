package dev.casperrs.cookieClicker;

import dev.casperrs.cookieClicker.config.PluginSettings;
import dev.casperrs.cookieClicker.config.YamlFiles;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EconomyConfig;
import dev.casperrs.cookieClicker.economy.EconomyConfigException;
import dev.casperrs.cookieClicker.economy.EconomyConfigParser;
import dev.casperrs.cookieClicker.sound.SoundConfigParser;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.station.StationFile;
import dev.casperrs.cookieClicker.station.StationRegistry;
import dev.casperrs.cookieClicker.text.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Loads every config file into its service, for enable and {@code /bakery reload}. Each file is
 * independent: a broken one is reported and keeps its previous (or disabled) state, the rest still load.
 * Reading these small files happens on the main thread at enable/reload only.
 */
final class BakeryConfigLoader {

    static final String BUILDINGS = "buildings.yml";
    static final String UPGRADES = "upgrades.yml";
    static final String SOUNDS = "sounds.yml";
    static final String MESSAGES = "messages.yml";

    private final JavaPlugin plugin;

    BakeryConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Problems are logged here and returned (for the reload command). */
    private Consumer<String> collector(List<String> problems) {
        return problem -> {
            plugin.getLogger().warning(problem);
            problems.add(problem);
        };
    }

    PluginSettings loadSettings(List<String> problems) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        return PluginSettings.from(plugin.getConfig(), plugin.getDataFolder().toPath(), collector(problems));
    }

    void loadMessages(Messages messages, List<String> problems) {
        try {
            messages.load(YamlFiles.loadWithDefaults(plugin, MESSAGES));
        } catch (YamlFiles.BadYamlException e) {
            collector(problems).accept(e.getMessage() + " (keeping the previous messages)");
        }
    }

    void loadSounds(SoundService sounds, List<String> problems) {
        Consumer<String> report = collector(problems);
        try {
            Optional<YamlConfiguration> yaml = YamlFiles.load(plugin, SOUNDS);
            if (yaml.isEmpty()) {
                report.accept(SOUNDS + " not found (data folder or jar): no sounds will play until it is added");
                sounds.load(java.util.Map.of());
                return;
            }
            sounds.load(SoundConfigParser.parse(YamlFiles.toMap(yaml.get()), report));
        } catch (YamlFiles.BadYamlException e) {
            report.accept(e.getMessage() + " (keeping the previous sounds)");
        }
    }

    /** On failure the economy keeps its current config ({@link EconomyConfig#DISABLED} at startup). */
    void loadBalance(Economy economy, Messages messages, List<String> problems) {
        Consumer<String> report = collector(problems);
        String keep = economy.config().enabled() ? "keeping the previous balance" : "economy disabled: clicks give nothing";
        try {
            Optional<YamlConfiguration> buildings = YamlFiles.load(plugin, BUILDINGS);
            Optional<YamlConfiguration> upgrades = YamlFiles.load(plugin, UPGRADES);
            if (buildings.isEmpty() || upgrades.isEmpty()) {
                report.accept((buildings.isEmpty() ? BUILDINGS : UPGRADES)
                        + " not found (data folder or jar); " + keep + ". Add it and run /bakery reload.");
                return;
            }
            EconomyConfigParser.Result result = EconomyConfigParser.parse(
                    YamlFiles.toMap(buildings.get()), YamlFiles.toMap(upgrades.get()));
            result.warnings().forEach(w -> plugin.getLogger().warning(w));
            economy.setConfig(result.config());
            for (String id : result.config().buildings().keySet()) {
                if (!messages.has("buildings." + id + ".name")) {
                    report.accept(MESSAGES + " has no name for building '" + id + "' (buildings." + id + ".name)");
                }
            }
        } catch (YamlFiles.BadYamlException | EconomyConfigException e) {
            report.accept(e.getMessage() + "; " + keep);
        }
    }

    void loadStations(StationFile file, StationRegistry registry, List<String> problems) {
        try {
            registry.replaceAll(file.load());
        } catch (YamlFiles.BadYamlException e) {
            collector(problems).accept(e.getMessage() + " (keeping the current stations)");
        }
    }

    static List<String> newProblemList() {
        return new ArrayList<>();
    }
}
