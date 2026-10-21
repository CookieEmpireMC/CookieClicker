package dev.casperrs.cookieClicker.economy;

import org.jspecify.annotations.Nullable;

/**
 * One upgrade from {@code upgrades.yml} (model.md "Upgrades").
 *
 * @param cost           one-time: the price; repeatable: the cost of level 1
 * @param value          the effect of <b>one</b> level
 * @param building       target building for {@link EffectType#BUILDING_MULTIPLIER}, else null
 * @param unlockBuilding building whose owned count unlocks this upgrade, or null
 * @param unlockOwned    owned count of {@code unlockBuilding} needed
 * @param unlockBaked    baked cookies needed
 * @param costGrowth     cost multiplier per level already owned; {@code 0} = one-time upgrade
 * @param maxLevel       highest level: 1 for one-time, {@code max-level} or {@link Integer#MAX_VALUE} for repeatable
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
