package dev.casperrs.cookieClicker.view.panel;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EffectType;
import dev.casperrs.cookieClicker.economy.UpgradeDef;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What one click upgrades panel row shows for the chosen bulk mode. Repeatable upgrades show their level, the cost
 * of {@code count} more levels and the +CPC they add; one-time upgrades show their price and effect and ignore the
 * mode (always bought once). Pure Java. count, cost and gain are 0 when maxed; count is the mode's ×N cut to the
 * levels left, or for Max the most affordable now (1 when none is, shown unaffordable); locked rows show
 * neededBuilding/neededOwned until reached, else neededBaked.
 */
public record UpgradeRowState(Style style, boolean repeatable, int level, int count, double cost, double gain,
                              double value, EffectType type, double neededBaked, @Nullable String neededBuilding,
                              int neededOwned) implements PanelRowState {

    public enum Style {
        AFFORDABLE, UNAFFORDABLE, LOCKED, MAXED;

        /** Key of the row background colour. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Null when the upgrade isn't in {@code upgrades.yml} (any more). */
    public static @Nullable UpgradeRowState of(Economy economy, CookiePouch pouch, String upgradeId, BulkMode mode) {
        UpgradeDef u = economy.config().upgrades().get(upgradeId);
        if (u == null) {
            return null;
        }
        int level = pouch.upgradeLevel(upgradeId);
        boolean conditionMet = economy.upgradeConditionMet(pouch, upgradeId);
        long left = (long) u.maxLevel() - level;
        int count;
        double cost;
        boolean affordable;
        Style style;
        if (left <= 0) {
            count = 0;
            cost = 0;
            affordable = false;
        } else if (!u.repeatable()) {
            count = 1;
            cost = u.cost();
            affordable = pouch.pouch() >= cost;
        } else if (mode.max()) {
            int n = economy.maxAffordableUpgrade(pouch, upgradeId);
            count = (int) Math.min(Math.max(1, n), left);
            cost = economy.upgradeBulkCost(pouch, upgradeId, count);
            affordable = n >= 1;
        } else {
            count = (int) Math.min(mode.count(), left);
            cost = economy.upgradeBulkCost(pouch, upgradeId, count);
            affordable = pouch.pouch() >= cost;
        }
        if (left <= 0) {
            style = Style.MAXED;
        } else if (!conditionMet) {
            style = Style.LOCKED;
        } else {
            style = affordable ? Style.AFFORDABLE : Style.UNAFFORDABLE;
        }
        double gain = count == 0 ? 0 : economy.clickPowerGain(pouch, upgradeId, count);
        String building = u.unlockBuilding() != null && pouch.owned(u.unlockBuilding()) < u.unlockOwned()
                ? u.unlockBuilding() : null;
        return new UpgradeRowState(style, u.repeatable(), level, count, cost, gain, u.value(), u.type(),
                u.unlockBaked(), building, u.unlockOwned());
    }

    /** The effect value as players read it: {@code click-cps-fraction} 0.01 → 1 (per cent), others unchanged. */
    public double shownValue() {
        return type == EffectType.CLICK_CPS_FRACTION ? value * 100 : value;
    }

    @Override
    public String colorKey() {
        return style.key();
    }

    @Override
    public boolean bright() {
        return style == Style.AFFORDABLE;
    }

    @Override
    public boolean rolls() {
        return style == Style.AFFORDABLE || style == Style.UNAFFORDABLE;
    }
}
