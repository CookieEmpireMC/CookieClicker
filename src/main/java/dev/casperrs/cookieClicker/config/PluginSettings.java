package dev.casperrs.cookieClicker.config;

import dev.casperrs.cookieClicker.storage.DatabaseSettings;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Everything in {@code config.yml}. Reloadable except {@code database}, which needs a restart. */
public record PluginSettings(DatabaseSettings database, int autosaveSeconds, int shutdownSaveTimeoutSeconds,
                             Hud hud, StationSettings station, ResourcePack resourcePack,
                             StationVisualSettings visuals) {

    /** Same settings with another database block (reload keeps the running database until a restart). */
    public PluginSettings withDatabase(DatabaseSettings db) {
        return new PluginSettings(db, autosaveSeconds, shutdownSaveTimeoutSeconds, hud, station, resourcePack, visuals);
    }

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
                resourcePack(c, problems),
                StationVisualSettings.from(c, problems));
    }

    private static ResourcePack resourcePack(ConfigurationSection c, Consumer<String> problems) {
        boolean enabled = c.getBoolean("resource-pack.enabled", true);
        String url = c.getString("resource-pack.url", "").trim();
        String sha1 = c.getString("resource-pack.sha1", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (enabled && !url.isEmpty()) {
            try {
                java.net.URI uri = new java.net.URI(url);
                if (uri.getScheme() == null || !uri.getScheme().startsWith("http")) {
                    problems.accept("config.yml: resource-pack.url must be an http(s) URL; the pack is not sent");
                    url = "";
                }
            } catch (java.net.URISyntaxException e) {
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
        return new ResourcePack(enabled, url, sha1,
                c.getBoolean("resource-pack.required", false),
                c.getString("resource-pack.prompt", "resource-pack.prompt"),
                Math.clamp(c.getInt("resource-pack.send-delay-ticks", 20), 1, 200));
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
