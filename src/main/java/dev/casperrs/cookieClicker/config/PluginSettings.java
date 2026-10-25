package dev.casperrs.cookieClicker.config;

import dev.casperrs.cookieClicker.storage.DatabaseSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Everything in {@code config.yml}. Reloadable except {@code database}, which needs a restart. Keys that are not
 * read here (older configs) are ignored.
 */
public record PluginSettings(DatabaseSettings database, int autosaveSeconds, int shutdownSaveTimeoutSeconds,
                             Hud hud, ResourcePack resourcePack, StationVisualSettings visuals, PlotSettings plot,
                             HouseSettings house, BeltSettings belt, FarmSettings farm) {

    /** messages.yml path of the text shown with the pack request. */
    private static final String PACK_PROMPT_KEY = "resource-pack.prompt";
    /** Lets other plugins' packs go first. */
    private static final int PACK_SEND_DELAY_TICKS = 20;
    private static final int HUD_REFRESH_SECONDS = 1;

    /** Same settings with another database block (reload keeps the running database until a restart). */
    public PluginSettings withDatabase(DatabaseSettings db) {
        return new PluginSettings(db, autosaveSeconds, shutdownSaveTimeoutSeconds, hud, resourcePack, visuals, plot,
                house, belt, farm);
    }

    /** Action bar re-send period; production always ticks once per second. */
    public record Hud(boolean enabled, int refreshSeconds) {
    }

    /**
     * Sending our pack on join. {@code promptKey} is a messages.yml path; {@code sha1} is lower-case hex or empty.
     * {@link #sendable()} is false when disabled or url/sha1 are missing/invalid.
     */
    public record ResourcePack(boolean enabled, String url, String sha1, boolean required, String promptKey,
                               int sendDelayTicks) {

        public boolean sendable() {
            return enabled && !url.isBlank() && sha1.matches("[0-9a-f]{40}");
        }
    }

    public static PluginSettings from(ConfigurationSection c, Path dataFolder, Consumer<String> problems) {
        return new PluginSettings(database(c, dataFolder, problems),
                positive(c, "database.autosave-seconds", 300, problems),
                positive(c, "database.shutdown-save-timeout-seconds", 10, problems),
                new Hud(c.getBoolean("hud.enabled", true), HUD_REFRESH_SECONDS),
                resourcePack(c, problems),
                StationVisualSettings.from(c, problems),
                PlotSettings.from(c, problems),
                HouseSettings.from(c, problems),
                BeltSettings.from(c, problems),
                FarmSettings.from(c, problems));
    }

    private static DatabaseSettings database(ConfigurationSection c, Path dataFolder, Consumer<String> problems) {
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
        return new DatabaseSettings(type,
                dataFolder.resolve(c.getString("database.h2.file", "data/cookies")),
                c.getString("database.mariadb.host", "localhost"),
                c.getInt("database.mariadb.port", 3306),
                c.getString("database.mariadb.database", "cookieclicker"),
                c.getString("database.mariadb.username", "cookieclicker"),
                c.getString("database.mariadb.password", ""),
                c.getString("database.mariadb.parameters", ""),
                prefix,
                positive(c, "database.mariadb.pool-size", DatabaseSettings.DEFAULT_POOL_SIZE, problems));
    }

    private static ResourcePack resourcePack(ConfigurationSection c, Consumer<String> problems) {
        boolean enabled = c.getBoolean("resource-pack.enabled", true);
        String url = c.getString("resource-pack.url", "").trim();
        String sha1 = c.getString("resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT);
        if (enabled && !url.isEmpty()) {
            try {
                String scheme = new URI(url).getScheme();
                if (!"http".equals(scheme) && !"https".equals(scheme)) {
                    problems.accept("config.yml: resource-pack.url must be an http(s) URL; the pack is not sent");
                    url = "";
                }
            } catch (URISyntaxException e) {
                problems.accept("config.yml: resource-pack.url is not a valid URL; the pack is not sent");
                url = "";
            }
        }
        if (enabled && !sha1.isEmpty() && !sha1.matches("[0-9a-f]{40}")) {
            problems.accept("config.yml: resource-pack.sha1 must be 40 hex characters; the pack is not sent");
            sha1 = "";
        }
        if (enabled && (url.isEmpty() || sha1.isEmpty())) {
            problems.accept("config.yml: resource-pack.url/sha1 are not set; players get vanilla fallbacks");
        }
        return new ResourcePack(enabled, url, sha1, c.getBoolean("resource-pack.required", false), PACK_PROMPT_KEY,
                PACK_SEND_DELAY_TICKS);
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
