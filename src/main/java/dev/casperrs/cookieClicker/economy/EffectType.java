package dev.casperrs.cookieClicker.economy;

import java.util.Arrays;
import java.util.Optional;

/** Upgrade effect types from {@code docs/balance/model.md}, keyed by their {@code upgrades.yml} name. */
public enum EffectType {
    CLICK_MULTIPLIER("click-multiplier"),
    CLICK_ADD("click-add"),
    CLICK_CPS_FRACTION("click-cps-fraction"),
    BUILDING_MULTIPLIER("building-multiplier"),
    GLOBAL_MULTIPLIER("global-multiplier");

    private final String configName;

    EffectType(String configName) {
        this.configName = configName;
    }

    public String configName() {
        return configName;
    }

    public static Optional<EffectType> fromConfigName(String name) {
        return Arrays.stream(values()).filter(t -> t.configName.equals(name)).findFirst();
    }
}
