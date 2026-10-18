package dev.casperrs.cookieClicker.config;

import dev.casperrs.cookieClicker.storage.DatabaseSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Everything in {@code config.yml}. Reloadable except {@code database}, which needs a restart. */
public record PluginSettings(DatabaseSettings database, int autosaveSeconds, int shutdownSaveTimeoutSeconds,
                             Hud hud, StationSettings station, ResourcePack resourcePack) {

    /** @param refreshSeconds how often the action bar is re-sent (production always ticks once per second) */
    public record Hud(boolean enabled, int refreshSeconds) {
    }

    /**
     * @param snapYawDegrees round the placing admin's yaw to this step (0 = keep it exact)
     * @param minDistance    {@code station set} refuses a spot closer than this to another station
     * @param removeRadius   {@code station remove} takes the nearest station within this radius
     */
    public record StationSettings(boolean snapToBlock, float snapYawDegrees, double minDistance, double removeRadius) {
    }

    /** Used by pass 2 (sending the pack on join). {@code promptKey} is a messages.yml path. */
    public record ResourcePack(boolean enabled, String url, String sha1, boolean required, String promptKey) {
    }

    public static PluginSettings from(ConfigurationSection c, Path dataFolder, Consumer<String> problems) {
        String rawType = c.getString("database.type", "h2");
        DatabaseSettings.Type type = DatabaseSettings.Type.parse(rawType).orElseGet(() -> {
            problems.accept("config.yml: database.type '" + rawType + "' is not h2 or mariadb; using h2");
            return DatabaseSettings.Type.H2;
        });
        String prefix = c.getString("database.table-prefix", "cc_");
        if (!prefix.matches("[A-Za-z0-9_]{0,32}")) {
            problems.accept("config.yml: database.table-prefix '" + prefix + "' may only use letters, digits and _; using cc_");
            prefix = "cc_";
        }
        DatabaseSettings db = new DatabaseSettings(type,
                dataFolder.resolve(c.getString("database.h2.file", "data/cookies")),
                c.getString("database.mariadb.host", "localhost"),
                c.getInt("database.mariadb.port", 3306),
                c.getString("database.mariadb.database", "cookieclicker"),
                c.getString("database.mariadb.username", "cookieclicker"),
                c.getString("database.mariadb.password", ""),
                c.getString("database.mariadb.parameters", ""),
                prefix);

        return new PluginSettings(db,
                positive(c, "database.autosave-seconds", 300, problems),
                positive(c, "database.shutdown-save-timeout-seconds", 10, problems),
                new Hud(c.getBoolean("hud.enabled", true), positive(c, "hud.refresh-seconds", 1, problems)),
                new StationSettings(c.getBoolean("station.snap-to-block", true),
                        (float) Math.max(0, c.getDouble("station.snap-yaw-degrees", 90)),
                        Math.max(0, c.getDouble("station.min-distance", 8)),
                        Math.max(0, c.getDouble("station.remove-radius", 8))),
                new ResourcePack(c.getBoolean("resource-pack.enabled", true),
                        c.getString("resource-pack.url", ""),
                        c.getString("resource-pack.sha1", ""),
                        c.getBoolean("resource-pack.required", false),
                        c.getString("resource-pack.prompt", "resource-pack.prompt")));
    }

    private static int positive(ConfigurationSection c, String path, int fallback, Consumer<String> problems) {
        int v = c.getInt(path, fallback);
        if (v <= 0) {
            problems.accept("config.yml: " + path + " must be > 0; using " + fallback);
            return fallback;
        }
        return v;
    }
}
