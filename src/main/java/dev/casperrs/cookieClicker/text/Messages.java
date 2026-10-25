package dev.casperrs.cookieClicker.text;

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
    private int revision;

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
        revision++;
    }

    /** Changes with every {@link #load}: caches of parsed texts compare it. */
    public int revision() {
        return revision;
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

    /** Display name of an upgrade from {@code upgrades.<id>.name}; the id when not configured. */
    public Component upgradeName(String upgradeId) {
        String path = "upgrades." + upgradeId + ".name";
        return yaml.isString(path) ? MM.deserialize(yaml.getString(path)) : Component.text(upgradeId);
    }

    /** Display name of an ingredient from {@code ingredients.<id>.name}; the id when not configured. */
    public Component ingredientName(String ingredientId) {
        String path = "ingredients." + ingredientId + ".name";
        return yaml.isString(path) ? MM.deserialize(yaml.getString(path)) : Component.text(ingredientId);
    }

    /** Display name of a recipe from {@code recipes.<id>.name}; the id when not configured. */
    public Component recipeName(String recipeId) {
        String path = "recipes." + recipeId + ".name";
        return yaml.isString(path) ? MM.deserialize(yaml.getString(path)) : Component.text(recipeId);
    }

    /** A recipe's flavour line from {@code recipes.<id>.lore}, as plain text; empty when not configured. */
    public String recipeLore(String recipeId) {
        return yaml.getString("recipes." + recipeId + ".lore", "");
    }

    /**
     * An ingredient's small icon: {@code ingredients.<id>.icon} (the pack glyph) or, without the pack or when that is
     * missing, {@code ingredients.<id>.icon-no-pack}; empty when neither is configured.
     */
    public Component ingredientIcon(String ingredientId, boolean withPack) {
        String glyph = "ingredients." + ingredientId + ".icon";
        String plain = "ingredients." + ingredientId + ".icon-no-pack";
        String path = withPack && yaml.isString(glyph) ? glyph : plain;
        return yaml.isString(path) ? MM.deserialize(yaml.getString(path)) : Component.empty();
    }

    /**
     * What one level of an upgrade does, from {@code upgrades.<id>.effect} with {@code <value>} (formatted) and
     * {@code <level>}; empty when not configured.
     */
    public Component upgradeEffect(String upgradeId, double value, int level) {
        String path = "upgrades." + upgradeId + ".effect";
        if (!yaml.isString(path)) {
            return Component.empty();
        }
        return MM.deserialize(yaml.getString(path), number("value", value),
                Placeholder.unparsed("level", Integer.toString(level)));
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
