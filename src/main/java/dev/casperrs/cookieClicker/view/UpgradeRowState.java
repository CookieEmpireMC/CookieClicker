package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;
import dev.casperrs.cookieClicker.economy.EffectType;
import dev.casperrs.cookieClicker.economy.UpgradeDef;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What one click upgrades panel row shows for the chosen bulk mode. Repeatable upgrades show their level, the cost
 * of {@code count} more levels and the +CPC they add; one-time upgrades show their price and effect and ignore the
 * mode (always bought once). Pure Java.
 *
 * @param count          levels a click buys: the ×N of the mode cut to the levels left below {@code max-level}, or
 *                       for Max the most affordable now (1 when none is: cost of 1, unaffordable look); 0 when maxed
 * @param cost           cost of {@code count} levels (one-time: the price); 0 when maxed
 * @param gain           how much the click power grows with those levels; 0 when maxed
 * @param value          the effect of one level ({@code upgrades.yml effect.value}), for the effect text
 * @param type           the effect type (the effect text shows {@code click-cps-fraction} as a percentage)
 * @param neededBaked    baked cookies needed to unlock (locked rows)
 * @param neededBuilding building whose count unlocks it while that count isn't reached yet, else null (locked rows
 *                       then show {@code neededBaked})
 * @param neededOwned    how many of {@code neededBuilding}
 */
public record UpgradeRowState(Style style, boolean repeatable, int level, int count, double cost, double gain,
                              double value, EffectType type, double neededBaked, @Nullable String neededBuilding,
                              int neededOwned) implements PanelRowState {

    public enum Style {
        AFFORDABLE, UNAFFORDABLE, LOCKED, MAXED;

        /** Key under {@code station.panel.colors}. */
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
