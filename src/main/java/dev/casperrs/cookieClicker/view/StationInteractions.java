package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.economy.ClickResult;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.PurchaseResult;
import dev.casperrs.cookieClicker.hud.Hud;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.vfx.Effects;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * What a click on the cookie or a shop row does: economy, then pop/effect/sound feedback, then HUD and panel.
 * The router has already checked owner, distance and dedupe. Main thread only.
 */
public final class StationInteractions {

    /** Effect/sound event names from the contract (effects.yml / sounds.yml). */
    static final String CLICK = "cookie_click";
    static final String CRIT = "cookie_crit";
    static final String PURCHASE = "purchase";
    static final String CANT_AFFORD = "cant_afford";
    /** How far the click effect is pulled out of the cookie toward the player (blocks). */
    private static final double TOWARD_PLAYER = 0.1;

    private final StationViews views;
    private final SessionManager sessions;
    private final Economy economy;
    private final Effects effects;
    private final SoundService sounds;
    private final Hud hud;
    private final Messages messages;

    public StationInteractions(StationViews views, SessionManager sessions, Economy economy, Effects effects,
                               SoundService sounds, Hud hud, Messages messages) {
        this.views = views;
        this.sessions = sessions;
        this.economy = economy;
        this.effects = effects;
        this.sounds = sounds;
        this.hud = hud;
        this.messages = messages;
    }

    /** @param hit world position of the hit on the hitbox, or null (left click: the cookie centre is used) */
    void clickCookie(Player player, StationView view, @Nullable Vector hit) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        if (pouch == null) {
            return;
        }
        ClickResult result = economy.click(pouch);
        views.beforePop(view); // spin segment first, so the pop's transformation is the last one this tick
        effects.pop(view.cookie, player);

        Vector at = hit != null ? hit.clone() : new Vector(view.cookieCentre[0], view.cookieCentre[1], view.cookieCentre[2]);
        Vector toEye = player.getEyeLocation().toVector().subtract(at);
        if (toEye.lengthSquared() > 1e-6) {
            at.add(toEye.normalize().multiply(TOWARD_PLAYER));
        }
        Location loc = at.toLocation(player.getWorld());
        String id = result.crit() ? CRIT : CLICK;
        effects.play(id, loc, player, Map.of("amount", messages.number(result.amount())));
        sounds.play(player, id, loc);
        hud.refresh(player, pouch);
        views.refreshPanels(player, pouch);
    }

    void clickRow(Player player, StationView view, String buildingId) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        if (pouch == null) {
            return;
        }
        StationView.Row row = view.rows.stream().filter(r -> r.buildingId.equals(buildingId)).findFirst().orElse(null);
        if (row == null) {
            return;
        }
        Location loc = new Location(player.getWorld(), row.centre[0], row.centre[1], row.centre[2]);
        // re-validated server-side: the panel may be up to one refresh behind
        if (economy.buyBuilding(pouch, buildingId) == PurchaseResult.BOUGHT) {
            effects.play(PURCHASE, loc, player);
            sounds.play(player, PURCHASE, loc);
            hud.refresh(player, pouch);
            views.refreshPanels(player, pouch);
        } else {
            sounds.play(player, CANT_AFFORD, loc);
        }
    }
}
