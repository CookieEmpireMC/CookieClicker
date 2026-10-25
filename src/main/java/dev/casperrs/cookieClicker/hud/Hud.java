package dev.casperrs.cookieClicker.hud;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.text.MarkerTemplate;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The action-bar HUD from {@code hud.action-bar}, or {@code hud.action-bar-no-pack} for players without our pack
 * (the glyph would render as a box). Each template is compiled once per text ({@link MarkerTemplate}). Once the
 * Recipe Book is owned, {@code hud.ingredients} (the ingredient stock) follows on the same line; the action bar fades,
 * so it is resent every second, but that group is only re-parsed when a shown amount changes. Main thread.
 */
public final class Hud {

    private final Messages messages;
    private final Economy economy;
    private final Predicate<Player> hasPack;
    private boolean enabled = true;
    /** messages.yml path -> the template of its current text (recompiled when a reload changes it). */
    private final Map<String, Compiled> compiled = new HashMap<>();
    /** Players whose action bar shows something else (a shelf tooltip) until this time (ms): the 1 Hz HUD waits. */
    private final Map<UUID, Long> heldUntil = new HashMap<>();

    /** Per player: the last ingredient group and the key (shown amounts) it was built for. */
    private final Map<UUID, Stock> stock = new HashMap<>();

    private record Compiled(String raw, MarkerTemplate template) {
    }

    private record Stock(String key, Component text) {
    }

    public Hud(Messages messages, Economy economy, Predicate<Player> hasPack) {
        this.messages = messages;
        this.economy = economy;
        this.hasPack = hasPack;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Builds the HUD line; separate from sending so it can be checked without a player. */
    public Component render(CookiePouch pouch, boolean withPack) {
        Component line = cookieLine(pouch, withPack);
        return showsStock(pouch) ? line.append(renderStock(pouch, withPack)) : line;
    }

    /** {@link #render(CookiePouch, boolean)}, with the ingredient group rebuilt only when its numbers changed. */
    private Component render(UUID player, CookiePouch pouch, boolean withPack) {
        Component line = cookieLine(pouch, withPack);
        if (!showsStock(pouch)) {
            stock.remove(player);
            return line;
        }
        String key = stockKey(pouch, withPack);
        Stock last = stock.get(player);
        if (last == null || !last.key().equals(key)) {
            last = new Stock(key, renderStock(pouch, withPack));
            stock.put(player, last);
        }
        return line.append(last.text());
    }

    private Component cookieLine(CookiePouch pouch, boolean withPack) {
        String path = withPack || !messages.has("hud.action-bar-no-pack") ? "hud.action-bar" : "hud.action-bar-no-pack";
        String raw = messages.raw(path);
        Compiled c = compiled.get(path);
        if (c == null || !c.raw().equals(raw)) {
            c = new Compiled(raw, MarkerTemplate.of(Messages.miniMessage(), raw, "cookies", "cps"));
            compiled.put(path, c);
        }
        return c.template().render(messages.number(pouch.pouch()), messages.number(economy.totalCps(pouch)));
    }

    private boolean showsStock(CookiePouch pouch) {
        return !economy.config().ingredients().isEmpty() && economy.recipesUnlocked(pouch);
    }

    /** The formatted amounts (what the player reads), the pack flag and the messages revision. */
    private String stockKey(CookiePouch pouch, boolean withPack) {
        StringBuilder key = new StringBuilder().append(withPack).append('|').append(messages.revision());
        for (String id : economy.config().ingredients()) {
            key.append('|').append(id).append('=').append(messages.number(pouch.ingredient(id)));
        }
        return key.toString();
    }

    /** {@code hud.ingredients} with {@code <stock>} = one {@code hud.ingredient} per ingredient. */
    private Component renderStock(CookiePouch pouch, boolean withPack) {
        String separator = messages.has("hud.ingredient-separator") ? messages.raw("hud.ingredient-separator") : " ";
        Component items = Component.empty();
        boolean first = true;
        for (String id : economy.config().ingredients()) {
            if (!first) {
                items = items.append(Messages.miniMessage().deserialize(separator));
            }
            first = false;
            items = items.append(messages.component("hud.ingredient",
                    Placeholder.component("icon", messages.ingredientIcon(id, withPack)),
                    Placeholder.component("name", messages.ingredientName(id)),
                    Placeholder.unparsed("amount", messages.number(pouch.ingredient(id)))));
        }
        return messages.component("hud.ingredients", Placeholder.component("stock", items));
    }

    /** Sends the HUD now (after a click or purchase); ends a tooltip that is still showing. */
    public void refresh(Player player, CookiePouch pouch) {
        heldUntil.remove(player.getUniqueId());
        if (enabled) {
            player.sendActionBar(render(player.getUniqueId(), pouch, hasPack.test(player)));
        }
    }

    /** The ticker's once-a-second HUD: skipped while a tooltip is showing in the action bar. */
    public void refreshScheduled(Player player, CookiePouch pouch) {
        Long until = heldUntil.get(player.getUniqueId());
        if (until != null) {
            if (System.currentTimeMillis() < until) {
                return;
            }
            heldUntil.remove(player.getUniqueId());
        }
        refresh(player, pouch);
    }

    /**
     * Shows {@code text} in the action bar for about {@code seconds} (e.g. a shelf icon's name and effect); the
     * 1 Hz HUD waits that long, a click or purchase brings it back at once.
     */
    public void showTooltip(Player player, Component text, int seconds) {
        heldUntil.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        player.sendActionBar(text);
    }

    /** Forget a player (quit). */
    public void forget(UUID player) {
        heldUntil.remove(player);
        stock.remove(player);
    }
}
