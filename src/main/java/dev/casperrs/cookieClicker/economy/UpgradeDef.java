package dev.casperrs.cookieClicker.economy;

import org.jspecify.annotations.Nullable;

/**
 * One upgrade from {@code upgrades.yml} (model.md "Upgrades"). value is <b>one</b> level's effect. One-time:
 * costGrowth 0, maxLevel 1, cost = the price; repeatable: cost of level 1, maxLevel {@code max-level} or
 * {@link Integer#MAX_VALUE}.
 */
public record UpgradeDef(String id, double cost, EffectType type, double value,
                         @Nullable String building, @Nullable String unlockBuilding,
                         int unlockOwned, double unlockBaked, double costGrowth, int maxLevel) {

    /** A one-time upgrade (no {@code cost-growth}). */
    public UpgradeDef(String id, double cost, EffectType type, double value,
                      @Nullable String building, @Nullable String unlockBuilding,
                      int unlockOwned, double unlockBaked) {
        this(id, cost, type, value, building, unlockBuilding, unlockOwned, unlockBaked, 0, 1);
    }

    /** Has {@code cost-growth}: can be bought again, level by level, up to {@link #maxLevel()}. */
    public boolean repeatable() {
        return costGrowth > 0;
    }

    /**
     * The effect at {@code level} (model.md "Effect at level L"): additive types {@code value × L},
     * multiplier types {@code value ^ L}. Level 0 gives the neutral value (0 or 1).
     */
    public double effectAt(int level) {
        return type.multiplier() ? Math.pow(value, level) : value * level;
    }
}
