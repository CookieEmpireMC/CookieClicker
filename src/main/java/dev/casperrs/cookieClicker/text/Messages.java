package dev.casperrs.cookieClicker.text;

import dev.casperrs.cookieClicker.format.NumberFormatter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * All player-facing text, from {@code messages.yml} as MiniMessage (one shared {@link MiniMessage}).
 * Dynamic values go in as placeholders; anything from players only via {@link Placeholder#unparsed}.
 * Main thread only (reload swaps the file).
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Logger log;
    private final Set<String> warned = new HashSet<>();
    private ConfigurationSection yaml = new YamlConfiguration();
    private NumberFormatter numbers = new NumberFormatter(numberSettings(new YamlConfiguration()));

    public Messages(Logger log) {
        this.log = log;
    }

    public static MiniMessage miniMessage() {
        return MM;
    }

    /** Swap in a freshly loaded {@code messages.yml} (with the jar copy as defaults). */
    public void load(ConfigurationSection messages) {
        this.yaml = messages;
        this.numbers = new NumberFormatter(numberSettings(messages));
        warned.clear();
    }

    public NumberFormatter numbers() {
        return numbers;
    }

    /** Formatted cookie amount, e.g. {@code 1.2M}. Plain text. */
    public String number(double value) {
        return numbers.format(value);
    }

    /** Placeholder with a formatted number (inserted unparsed). */
    public TagResolver number(String name, double value) {
        return Placeholder.unparsed(name, numbers.format(value));
    }

    /** Raw MiniMessage string at {@code path}; the path itself when missing (logged once). */
    public String raw(String path) {
        String s = yaml.getString(path);
        if (s == null) {
            if (warned.add(path)) {
                log.warning("messages.yml is missing '" + path + "'");
            }
            return path;
        }
        return s;
    }

    public boolean has(String path) {
        return yaml.isString(path);
    }

    public Component component(String path, TagResolver... resolvers) {
        return MM.deserialize(raw(path), resolvers);
    }

    public void send(Audience to, String path, TagResolver... resolvers) {
        to.sendMessage(component(path, TagResolver.resolver(prefix(), TagResolver.resolver(resolvers))));
    }

    /** {@code <prefix>} for command replies. */
    private TagResolver prefix() {
        return Placeholder.parsed("prefix", yaml.getString("prefix", ""));
    }

    /** Display name of a building from {@code buildings.<id>.name}; the id when not configured. */
    public Component buildingName(String buildingId) {
        String path = "buildings." + buildingId + ".name";
        return yaml.isString(path) ? MM.deserialize(yaml.getString(path)) : Component.text(buildingId);
    }

    static NumberFormatter.Settings numberSettings(ConfigurationSection m) {
        List<String> suffixes = m.getStringList("numbers.suffixes");
        return new NumberFormatter.Settings(suffixes,
                m.getString("numbers.large-format", "{value}{suffix}"),
                m.getString("numbers.scientific-format", "{value}e{exponent}"),
                m.getInt("numbers.decimals", 1),
                m.getInt("numbers.small-decimals", 1),
                m.getString("numbers.grouping-separator", ","),
                m.getString("numbers.decimal-separator", "."),
                m.getString("numbers.infinity", "infinity"));
    }
}
