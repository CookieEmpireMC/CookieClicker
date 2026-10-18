package dev.casperrs.cookieClicker.economy;

import org.jspecify.annotations.Nullable;

/**
 * One upgrade from {@code upgrades.yml}.
 *
 * @param building       target building for {@link EffectType#BUILDING_MULTIPLIER}, else null
 * @param unlockBuilding building whose owned count unlocks this upgrade, or null
 * @param unlockOwned    owned count of {@code unlockBuilding} needed
 * @param unlockBaked    baked cookies needed
 */
public record UpgradeDef(String id, double cost, EffectType type, double value,
                         @Nullable String building, @Nullable String unlockBuilding,
                         int unlockOwned, double unlockBaked) {
}
