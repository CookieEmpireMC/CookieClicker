package dev.casperrs.cookieClicker.recipe;

import dev.casperrs.cookieClicker.config.RecipeGuiSettings;
import dev.casperrs.cookieClicker.economy.RecipeBook;
import dev.casperrs.cookieClicker.economy.RecipeDef;
import dev.casperrs.cookieClicker.economy.RecipeStatus;
import dev.casperrs.cookieClicker.log.WarnOnce;
import dev.casperrs.cookieClicker.text.Messages;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * The Recipe Book icon of one recipe in its three looks: locked, available (costs, missing parts in red) and bought
 * (glint, "Baked!"). Name and lore are our own ({@code ITEM_NAME} + {@code LORE}); vanilla tooltip lines are hidden.
 * Main thread only.
 */
final class RecipeIcons {

    private static final String LOCKED_MODEL = "recipe/locked";

    private final Messages messages;
    private final Supplier<RecipeGuiSettings> settings;
    private final WarnOnce warnings;

    RecipeIcons(Messages messages, Supplier<RecipeGuiSettings> settings, WarnOnce warnings) {
        this.messages = messages;
        this.settings = settings;
        this.warnings = warnings;
    }

    /** Whole units a player has, shown rounded down (a stock a hair below a whole number counts as that number). */
    static double shownHave(double have) {
        return Math.floor(have * (1 + RecipeBook.TOLERANCE));
    }

    /** Whole units still missing, shown rounded up. */
    static double shownMissing(double missing) {
        return Math.ceil(missing * (1 - RecipeBook.TOLERANCE));
    }

    /**
     * What the icon shows, as a string: equal keys build equal items, so an open book only re-sends a slot whose key
     * changed.
     */
    String key(RecipeStatus s, ToDoubleFunction<String> stock, boolean withPack) {
        StringBuilder key = new StringBuilder().append(messages.revision()).append('|').append(withPack)
                .append('|').append(s.state());
        if (s.state() == RecipeStatus.State.AVAILABLE) {
            key.append('|').append(messages.number(shownMissing(s.missingCookies())));
            s.recipe().ingredients().forEach((id, need) -> key.append('|')
                    .append(messages.number(shownHave(stock.applyAsDouble(id)))).append('/')
                    .append(messages.number(shownMissing(s.missingIngredients().getOrDefault(id, 0.0)))));
        }
        return key.toString();
    }

    ItemStack icon(RecipeStatus s, ToDoubleFunction<String> stock, boolean withPack) {
        RecipeDef r = s.recipe();
        boolean locked = s.state() == RecipeStatus.State.LOCKED;
        ItemStack item = ItemStack.of(material(locked ? RecipeGuiSettings.LOCKED : r.id()));
        if (withPack) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key("cookieclicker", locked ? LOCKED_MODEL : "recipe/" + r.id()));
        }
        TagResolver name = Placeholder.component("name", messages.recipeName(r.id()));
        List<Component> lore = new ArrayList<>();
        Component title;
        switch (s.state()) {
            case LOCKED -> {
                title = messages.component("gui.recipes.locked-name", name);
                lore.add(line("gui.recipes.locked", messages.number("needed", r.unlockBaked())));
            }
            case BOUGHT -> {
                title = messages.recipeName(r.id());
                effect(lore, r);
                lore.add(line("gui.recipes.baked"));
                item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            default -> {
                title = messages.recipeName(r.id());
                effect(lore, r);
                lore.add(line("gui.recipes.costs"));
                lore.add(cookies(s));
                for (Map.Entry<String, Double> e : r.ingredients().entrySet()) {
                    lore.add(ingredient(s, e.getKey(), e.getValue(), stock.applyAsDouble(e.getKey()), withPack));
                }
                lore.add(line(s.affordable() ? "gui.recipes.click" : "gui.recipes.short"));
            }
        }
        item.setData(DataComponentTypes.ITEM_NAME, title);
        item.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ENCHANTMENTS, DataComponentTypes.ATTRIBUTE_MODIFIERS,
                        DataComponentTypes.STORED_ENCHANTMENTS, DataComponentTypes.POTION_CONTENTS)
                .build());
        return item;
    }

    private void effect(List<Component> lore, RecipeDef r) {
        String flavour = messages.recipeLore(r.id());
        if (!flavour.isEmpty()) {
            lore.add(line("gui.recipes.flavour", Placeholder.unparsed("lore", flavour)));
        }
        lore.add(line("gui.recipes.effect", messages.number("percent", r.cpsPercent() * 100)));
    }

    private Component cookies(RecipeStatus s) {
        TagResolver cost = messages.number("cost", s.recipe().cost());
        return s.missesCookies()
                ? line("gui.recipes.cookies-missing", cost, messages.number("missing", shownMissing(s.missingCookies())))
                : line("gui.recipes.cookies", cost);
    }

    private Component ingredient(RecipeStatus s, String id, double need, double have, boolean withPack) {
        TagResolver values = TagResolver.resolver(
                Placeholder.component("icon", messages.ingredientIcon(id, withPack)),
                Placeholder.component("name", messages.ingredientName(id)),
                messages.number("have", shownHave(have)),
                messages.number("need", need));
        Double missing = s.missingIngredients().get(id);
        return missing == null
                ? line("gui.recipes.ingredient", values)
                : line("gui.recipes.ingredient-missing", values, messages.number("missing", shownMissing(missing)));
    }

    /** Lore and item names are italic by default: our lines are upright unless the template says otherwise. */
    private Component line(String path, TagResolver... values) {
        return messages.component(path, values).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private Material material(String key) {
        String name = settings.get().fallbackFor(key);
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem() || m.isAir()) {
            warnings.warn("recipe-icon:" + key, "config.yml: recipes.gui.fallback-items." + key + " '" + name
                    + "' is not an item; using " + RecipeGuiSettings.DEFAULT_ITEM);
            return Material.BOOK;
        }
        return m;
    }
}
