package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.view.panel.BulkMode;
import dev.casperrs.cookieClicker.view.panel.PanelKind;
import dev.casperrs.cookieClicker.economy.BulkPurchase;
import dev.casperrs.cookieClicker.economy.ClickResult;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EffectType;
import dev.casperrs.cookieClicker.economy.UpgradeDef;
import dev.casperrs.cookieClicker.hud.Hud;
import dev.casperrs.cookieClicker.session.SessionManager;
import dev.casperrs.cookieClicker.sound.SoundService;
import dev.casperrs.cookieClicker.text.Messages;
import dev.casperrs.cookieClicker.vfx.Effects;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;
import java.util.UUID;

/**
 * What a click on the cookie, a panel row, a panel's ×N toggle or a shelf icon does: economy, then
 * pop/effect/sound feedback, then HUD and panels. The router has already checked owner, distance and dedupe. A bulk
 * buy is one atomic economy call with one sound, one effect and one HUD/panel refresh. Main thread only.
 */
public final class StationInteractions {

    static final String CLICK = "cookie_click";
    static final String CRIT = "cookie_crit";
    static final String PURCHASE = "purchase";
    static final String CANT_AFFORD = "cant_afford";
    static final String UPGRADE_BOUGHT = "upgrade_bought";
    static final String UPGRADE_SHELVED = "upgrade_shelved";
    static final String BASKET_TIER_UP = "basket_tier_up";
    static final String BULK_TOGGLE = "bulk_toggle";

    private final StationViews views;
    private final SessionManager sessions;
    private final Economy economy;
    private final Effects effects;
    private final SoundService sounds;
    private final Hud hud;
    private final Messages messages;
    /** Told after every building purchase that went through (the visible buildings). */
    private Consumer<Player> buildingsBought = player -> { };

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

    public void setBuildingsBought(Consumer<Player> buildingsBought) {
        this.buildingsBought = buildingsBought;
    }

    /** {@code hit} is the world hit position, or null (left click) for the cookie centre. */
    void clickCookie(Player player, StationView view, @Nullable Vector hit) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        if (pouch == null) {
            return;
        }
        ClickResult result = economy.click(pouch);
        views.beforePop(view); // spin segment first, so the pop's transformation is the last one this tick
        // the pop carries the spin and bob in its own keyframes, so the cookie never stops turning while clicked
        effects.pop(view.cookie, player, Effects.POP, views.popMotion(view));

        Vector at = hit != null ? hit.clone() : new Vector(view.cookieCentre[0], view.cookieCentre[1], view.cookieCentre[2]);
        Vector toEye = player.getEyeLocation().toVector().subtract(at);
        if (toEye.lengthSquared() > 1e-6) {
            at.add(toEye.normalize().multiply(views.settings().cookie().clickEffectOffset()));
        }
        Location loc = at.toLocation(player.getWorld());
        String id = result.crit() ? CRIT : CLICK;
        effects.play(id, loc, player, Map.of("amount", messages.number(result.amount())));
        sounds.play(player, id, loc);
        hud.refresh(player, pouch);
        views.refreshPanels(player, pouch);
    }

    /** A row slot was clicked: buy what it shows, for the panel's bulk mode (re-validated server-side). */
    void clickRow(Player player, StationView view, PanelKind kind, int index) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        StationView.Panel panel = view.panel(kind);
        if (pouch == null || index < 0 || index >= panel.rows.size()) {
            return;
        }
        StationView.Row row = panel.rows.get(index);
        String id = row.id;
        if (id == null) {
            return; // empty slot
        }
        Location loc = new Location(player.getWorld(), row.centre[0], row.centre[1], row.centre[2]);
        BulkMode mode = views.mode(player.getUniqueId(), kind);
        String effect = PURCHASE;
        boolean oneTime = false;
        BulkPurchase result;
        if (kind == PanelKind.BUILDINGS) {
            result = mode.max() ? economy.buyMaxBuildings(pouch, id) : economy.buyBuildings(pouch, id, mode.count());
        } else {
            UpgradeDef u = economy.config().upgrades().get(id);
            if (u == null) {
                return;
            }
            if (!u.repeatable()) {
                oneTime = true;
                effect = UPGRADE_BOUGHT;
                result = economy.buyUpgrades(pouch, id, 1);
            } else if (mode.max()) {
                result = economy.buyMaxUpgrades(pouch, id);
            } else {
                // near max-level a ×N buys the levels that are left (the row shows that count and its cost)
                long left = (long) u.maxLevel() - pouch.upgradeLevel(id);
                result = economy.buyUpgrades(pouch, id, (int) Math.clamp(left, 1, mode.count()));
            }
        }
        if (!result.bought()) {
            sounds.play(player, CANT_AFFORD, loc); // never a silent no-op, also for Max with nothing affordable
            return;
        }
        if (oneTime) {
            Location iconLoc = new Location(player.getWorld(), row.iconAt[0], row.iconAt[1], row.iconAt[2]);
            effects.play(effect, iconLoc, player);
            views.launchToShelf(player, view, row, id, pouch);
        } else {
            effects.play(effect, loc, player);
        }
        sounds.play(player, PURCHASE, loc);
        hud.refresh(player, pouch);
        views.refreshPanels(player, pouch, true);
        if (kind == PanelKind.BUILDINGS) {
            buildingsBought.accept(player);
        }
    }

    /**
     * The ×1/×5/×10/Max toggle of a panel was clicked: on a label that mode, elsewhere on the toggle the next mode;
     * a tick sound, and the prices roll.
     */
    void clickToggle(Player player, StationView view, PanelKind kind, @Nullable BulkMode label) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        if (pouch == null) {
            return;
        }
        if (label == null) {
            views.cycleMode(player, kind);
        } else {
            views.setMode(player, kind, label);
        }
        StationView.Panel panel = view.panel(kind);
        sounds.play(player, BULK_TOGGLE, panel.toggle.getLocation());
        views.refreshPanels(player, pouch, true);
    }

    /**
     * A ▲ / ▼ page label was clicked: the panel scrolls one row ({@code delta} -1 = up) with the toggle's tick sound;
     * at the end of the list (a greyed label) nothing happens.
     */
    void clickPage(Player player, StationView view, PanelKind kind, int delta) {
        CookiePouch pouch = sessions.get(player.getUniqueId()).orElse(null);
        if (pouch == null || !views.scroll(view, kind, pouch, delta)) {
            return;
        }
        sounds.play(player, BULK_TOGGLE, view.panel(kind).toggle.getLocation());
        views.refreshPanels(player, pouch, true);
    }

    /** A shelf icon was clicked: its name and effect in the action bar for a few seconds. */
    void clickShelf(Player player, String upgradeId) {
        UpgradeDef u = economy.config().upgrades().get(upgradeId);
        if (u == null) {
            return;
        }
        double value = u.type() == EffectType.CLICK_CPS_FRACTION
                ? u.value() * 100 : u.value();
        hud.showTooltip(player, messages.component("panel.shelf.tooltip",
                Placeholder.component("name", messages.upgradeName(upgradeId)),
                Placeholder.component("effect", messages.upgradeEffect(upgradeId, value, 1))),
                views.settings().shelf().tooltipSeconds());
    }

    /** A bought upgrade's icon landed on its shelf slot. */
    public void iconShelved(Player player, Location at) {
        effects.play(UPGRADE_SHELVED, at, player);
        sounds.play(player, UPGRADE_SHELVED, at);
    }

    /** The owner's cookie basket reached a higher tier ({@code at} = the basket's bottom centre). */
    public void basketTierUp(Player player, Location at) {
        effects.play(BASKET_TIER_UP, at, player);
        sounds.play(player, BASKET_TIER_UP, at);
    }

    void forget(UUID player) {
        hud.forget(player);
    }
}
