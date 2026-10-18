package dev.casperrs.cookieClicker.hud;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.text.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The action-bar HUD: cookie glyph + cookies + CPS from {@code messages.yml}
 * ({@code hud.action-bar}, or {@code hud.action-bar-no-pack} for players without our resource pack,
 * whose glyph would render as a box). Each template is parsed once per text (see {@link HudTemplate}), not on
 * every refresh. Main thread only.
 */
public final class Hud {

    private final Messages messages;
    private final Economy economy;
    private final Predicate<Player> hasPack;
    private boolean enabled = true;
    /** messages.yml path -> the template compiled from its current text (recompiled when a reload changes it). */
    private final Map<String, Compiled> compiled = new HashMap<>();

    private record Compiled(String raw, @Nullable HudTemplate template) {
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
            c = new Compiled(raw, HudTemplate.compile(Messages.miniMessage(), raw));
            compiled.put(path, c);
        }
        if (c.template() == null || cookies.isEmpty() || cps.isEmpty()) {
            return HudTemplate.parse(Messages.miniMessage(), raw, cookies, cps);
        }
        return c.template().render(cookies, cps);
    }

    /** Sends the HUD now (the ticker does this every second; pass 2 also calls it right after a click/purchase). */
    public void refresh(Player player, CookiePouch pouch) {
        if (enabled) {
            player.sendActionBar(render(pouch, hasPack.test(player)));
        }
    }
}
