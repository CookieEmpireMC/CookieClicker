package dev.casperrs.cookieClicker.hud;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.text.MarkerTemplate;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The action-bar HUD from {@code hud.action-bar}, or {@code hud.action-bar-no-pack} for players without our pack
 * (the glyph would render as a box). Each template is compiled once per text ({@link MarkerTemplate}). Main thread.
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

    private record Compiled(String raw, MarkerTemplate template) {
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
        String path = withPack || !messages.has("hud.action-bar-no-pack") ? "hud.action-bar" : "hud.action-bar-no-pack";
        String raw = messages.raw(path);
        String cookies = messages.number(pouch.pouch());
        String cps = messages.number(economy.totalCps(pouch));
        Compiled c = compiled.get(path);
        if (c == null || !c.raw().equals(raw)) {
            c = new Compiled(raw, MarkerTemplate.of(Messages.miniMessage(), raw, "cookies", "cps"));
            compiled.put(path, c);
        }
        return c.template().render(cookies, cps);
    }

    /** Sends the HUD now (after a click or purchase); ends a tooltip that is still showing. */
    public void refresh(Player player, CookiePouch pouch) {
        heldUntil.remove(player.getUniqueId());
        if (enabled) {
            player.sendActionBar(render(pouch, hasPack.test(player)));
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
    }
}
