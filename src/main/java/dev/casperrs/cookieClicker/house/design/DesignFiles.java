package dev.casperrs.cookieClicker.house.design;

import dev.casperrs.cookieClicker.config.YamlFiles;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/** The design files under {@code houses/}: read from the data folder first (admins can override), then the jar. */
public final class DesignFiles {

    public static final String DIR = "houses";
    public static final String DEFAULT_DESIGN = "factory";
    public static final String FENCE_STYLE = "fence.yml";
    public static final String BELT_STYLE = "belt.yml";
    public static final String DECOR = "decor.yml";
    public static final String BUILDING_SLOTS = "building-slots.yml";

    private DesignFiles() {
    }

    /** The text of {@code houses/<file>}, or null when neither the data folder nor the jar has it. Blocking IO. */
    public static @Nullable String read(Plugin plugin, String file) throws IOException {
        Path own = plugin.getDataFolder().toPath().resolve(DIR).resolve(file);
        if (Files.isRegularFile(own)) {
            return Files.readString(own, StandardCharsets.UTF_8);
        }
        try (InputStream in = plugin.getResource(DIR + "/" + file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static Map<String, Object> parseYaml(String text) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return YamlFiles.toMap(y);
    }

    /** Runs {@code parser} on the YAML in {@code text}; text that is no YAML gives {@code onBadYaml(problem)}. */
    public static <R> R parse(String text, Function<Map<String, Object>, R> parser, Function<String, R> onBadYaml) {
        try {
            return parser.apply(parseYaml(text));
        } catch (InvalidConfigurationException e) {
            return onBadYaml.apply("not valid YAML: " + e.getMessage());
        }
    }
}
