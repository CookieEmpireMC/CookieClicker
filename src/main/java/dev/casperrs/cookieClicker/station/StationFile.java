package dev.casperrs.cookieClicker.station;

import dev.casperrs.cookieClicker.config.YamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/** Reads {@code stations.yml} (enable/reload) and writes it off the main thread after changes. */
public final class StationFile {

    public static final String NAME = "stations.yml";

    private final JavaPlugin plugin;
    private final Executor io;

    public StationFile(JavaPlugin plugin, Executor io) {
        this.plugin = plugin;
        this.io = io;
    }

    public List<Station> load() throws YamlFiles.BadYamlException {
        return YamlFiles.load(plugin, NAME)
                .map(yaml -> StationCodec.fromMap(YamlFiles.toMap(yaml),
                        problem -> plugin.getLogger().warning(problem)))
                .orElseGet(List::of);
    }

    /** Serialises on the calling (main) thread, writes atomically on {@code io}. */
    public void saveAsync(Collection<Station> stations) {
        YamlConfiguration yaml = new YamlConfiguration();
        StationCodec.toMap(stations).forEach((key, value) -> {
            if (value instanceof java.util.Map<?, ?> map) {
                yaml.createSection(key, map);
            } else {
                yaml.set(key, value);
            }
        });
        String text = yaml.saveToString();
        Path target = plugin.getDataFolder().toPath().resolve(NAME);
        io.execute(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling(NAME + ".tmp");
                Files.writeString(tmp, text, StandardCharsets.UTF_8);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not write " + NAME, e);
            }
        });
    }
}
