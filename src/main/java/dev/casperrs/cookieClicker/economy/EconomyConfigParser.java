package dev.casperrs.cookieClicker.economy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the raw YAML trees of {@code buildings.yml} and {@code upgrades.yml} into an {@link EconomyConfig}.
 * Same validation as {@code tools/balance/model.py}. Pure Java: feed it plain nested maps
 * (e.g. from SnakeYAML or {@code ConfigUtil.toMap}).
 */
public final class EconomyConfigParser {

    /** Parsed config plus non-fatal notes (e.g. crit keys missing, so crits are off). */
    public record Result(EconomyConfig config, List<String> warnings) {
    }

    private EconomyConfigParser() {
    }

    public static Result parse(Map<String, ?> buildingsYaml, Map<String, ?> upgradesYaml) throws EconomyConfigException {
        List<String> warnings = new ArrayList<>();

        Map<String, BuildingDef> buildings = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : section(buildingsYaml, "buildings", "buildings.yml").entrySet()) {
            String id = e.getKey();
            String where = "buildings.yml: buildings." + id;
            Map<String, ?> spec = asMap(e.getValue(), where);
            Map<String, ?> unlock = optionalMap(spec.get("unlock"), where + ".unlock");
            buildings.put(id, new BuildingDef(id,
                    number(spec.get("base-cost"), where + ".base-cost"),
                    number(spec.get("base-cps"), where + ".base-cps"),
                    unlock.containsKey("baked") ? number(unlock.get("baked"), where + ".unlock.baked") : 0));
        }
        if (buildings.isEmpty()) {
            throw new EconomyConfigException("buildings.yml has no buildings");
        }

        Map<String, UpgradeDef> upgrades = new LinkedHashMap<>();
        Object rawUpgrades = upgradesYaml.get("upgrades");
        for (Map.Entry<String, ?> e : optionalMap(rawUpgrades, "upgrades.yml: upgrades").entrySet()) {
            String id = e.getKey();
            String where = "upgrades.yml: upgrades." + id;
            Map<String, ?> spec = asMap(e.getValue(), where);
            Map<String, ?> effect = optionalMap(spec.get("effect"), where + ".effect");
            Map<String, ?> unlock = optionalMap(spec.get("unlock"), where + ".unlock");
            Object typeName = effect.get("type");
            EffectType type = EffectType.fromConfigName(String.valueOf(typeName)).orElseThrow(() ->
                    new EconomyConfigException(where + ".effect.type '" + typeName + "' is not a known effect type"));
            String target = effect.get("building") == null ? null : String.valueOf(effect.get("building"));
            if (type == EffectType.BUILDING_MULTIPLIER && (target == null || !buildings.containsKey(target))) {
                throw new EconomyConfigException(where + ".effect.building '" + target + "' is not a building");
            }
            String unlockBuilding = unlock.get("building") == null ? null : String.valueOf(unlock.get("building"));
            if (unlockBuilding != null && !buildings.containsKey(unlockBuilding)) {
                throw new EconomyConfigException(where + ".unlock.building '" + unlockBuilding + "' is not a building");
            }
            int unlockOwned = unlock.containsKey("owned") ? (int) number(unlock.get("owned"), where + ".unlock.owned") : 0;
            double unlockBaked = unlock.containsKey("baked") ? number(unlock.get("baked"), where + ".unlock.baked") : 0;
            upgrades.put(id, new UpgradeDef(id, number(spec.get("cost"), where + ".cost"), type,
                    number(effect.get("value"), where + ".effect.value"), target, unlockBuilding, unlockOwned, unlockBaked));
        }

        double growth = number(buildingsYaml.get("cost-growth"), "buildings.yml: cost-growth");
        if (growth < 1) {
            throw new EconomyConfigException("buildings.yml: cost-growth must be >= 1, got " + growth);
        }
        Map<String, ?> click = section(upgradesYaml, "click", "upgrades.yml");
        double basePower = number(click.get("base-power"), "upgrades.yml: click.base-power");

        double critChance = 0;
        double critMultiplier = 1;
        if (click.containsKey("crit-chance") && click.containsKey("crit-multiplier")) {
            critChance = number(click.get("crit-chance"), "upgrades.yml: click.crit-chance");
            if (critChance > 1) {
                throw new EconomyConfigException("upgrades.yml: click.crit-chance must be between 0 and 1, got " + critChance);
            }
            critMultiplier = number(click.get("crit-multiplier"), "upgrades.yml: click.crit-multiplier");
        } else {
            warnings.add("upgrades.yml: click.crit-chance / click.crit-multiplier missing, crits are off");
        }
        return new Result(new EconomyConfig(growth, basePower, critChance, critMultiplier, buildings, upgrades), warnings);
    }

    private static Map<String, ?> section(Map<String, ?> root, String key, String file) throws EconomyConfigException {
        Object raw = root.get(key);
        if (raw == null) {
            throw new EconomyConfigException(file + " is missing '" + key + "'");
        }
        return asMap(raw, file + ": " + key);
    }

    private static Map<String, ?> optionalMap(Object raw, String where) throws EconomyConfigException {
        return raw == null ? Map.of() : asMap(raw, where);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> asMap(Object raw, String where) throws EconomyConfigException {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new EconomyConfigException(where + " must be a section, got " + raw);
        }
        return (Map<String, ?>) map;
    }

    private static double number(Object raw, String where) throws EconomyConfigException {
        if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() < 0) {
            throw new EconomyConfigException(where + " must be a non-negative number, got " + raw);
        }
        return n.doubleValue();
    }
}
