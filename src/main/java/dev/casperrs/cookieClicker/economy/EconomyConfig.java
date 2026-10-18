package dev.casperrs.cookieClicker.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The parsed balance config ({@code buildings.yml} + {@code upgrades.yml}). Immutable; every number
 * comes from those files, none is defined in code.
 *
 * @param buildings in file order (the panel shows them in this order)
 */
public record EconomyConfig(double costGrowth, double basePower, double critChance, double critMultiplier,
                            Map<String, BuildingDef> buildings, Map<String, UpgradeDef> upgrades) {

    /**
     * Used while the balance files are missing or invalid: clicks give nothing, nothing can be bought.
     * The zeros mean "economy disabled", not balance values.
     */
    public static final EconomyConfig DISABLED = new EconomyConfig(1, 0, 0, 1, Map.of(), Map.of());

    public EconomyConfig {
        buildings = Collections.unmodifiableMap(new LinkedHashMap<>(buildings));
        upgrades = Collections.unmodifiableMap(new LinkedHashMap<>(upgrades));
    }

    public boolean enabled() {
        return this != DISABLED;
    }
}
