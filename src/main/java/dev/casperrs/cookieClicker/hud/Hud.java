package dev.casperrs.cookieClicker.hud;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/**
 * The action-bar HUD: cookie glyph + cookies + CPS from {@code messages.yml}
 * ({@code hud.action-bar}, or {@code hud.action-bar-no-pack} for players without our resource pack,
 * whose glyph would render as a box). Main thread only.
 */
public final class Hud {

    private final Messages messages;
    private final Economy economy;
    private final Predicate<Player> hasPack;
    private boolean enabled = true;

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
        return messages.component(path,
                messages.number("cookies", pouch.pouch()),
                messages.number("cps", economy.totalCps(pouch)));
    }

    /** Sends the HUD now (the ticker does this every second; pass 2 also calls it right after a click/purchase). */
    public void refresh(Player player, CookiePouch pouch) {
        if (enabled) {
            player.sendActionBar(render(pouch, hasPack.test(player)));
        }
    }
}
