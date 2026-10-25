package dev.casperrs.cookieClicker.economy;

import org.jspecify.annotations.Nullable;

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
        Map<String, BuildingDef> buildings = buildings(buildingsYaml);
        Map<String, UpgradeDef> upgrades = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : optionalMap(upgradesYaml.get("upgrades"), "upgrades.yml: upgrades").entrySet()) {
            upgrades.put(e.getKey(), upgrade(e.getKey(), e.getValue(), buildings));
        }
        Map<String, RecipeDef> recipes = recipes(upgradesYaml, buildings, upgrades);
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
        return new Result(new EconomyConfig(growth, basePower, critChance, critMultiplier, buildings, upgrades, recipes),
                warnings);
    }

    private static Map<String, BuildingDef> buildings(Map<String, ?> buildingsYaml) throws EconomyConfigException {
        List<Integer> milestones = milestones(buildingsYaml.get("milestones"), "buildings.yml: milestones");
        Map<String, BuildingDef> buildings = new LinkedHashMap<>();
        for (Map.Entry<String, ?> e : section(buildingsYaml, "buildings", "buildings.yml").entrySet()) {
            String id = e.getKey();
            String where = "buildings.yml: buildings." + id;
            Map<String, ?> spec = asMap(e.getValue(), where);
            Map<String, ?> unlock = optionalMap(spec.get("unlock"), where + ".unlock");
            double baseCost = number(spec.get("base-cost"), where + ".base-cost");
            if (baseCost <= 0) {
                throw new EconomyConfigException(where + ".base-cost must be > 0");
            }
            buildings.put(id, new BuildingDef(id, baseCost,
                    number(spec.get("base-cps"), where + ".base-cps"),
                    unlock.containsKey("baked") ? number(unlock.get("baked"), where + ".unlock.baked") : 0,
                    spec.containsKey("milestones") ? milestones(spec.get("milestones"), where + ".milestones") : milestones,
                    produces(spec.get("produces"), where + ".produces")));
        }
        if (buildings.isEmpty()) {
            throw new EconomyConfigException("buildings.yml has no buildings");
        }
        return buildings;
    }

    private static UpgradeDef upgrade(String id, Object raw, Map<String, BuildingDef> buildings)
            throws EconomyConfigException {
        String where = "upgrades.yml: upgrades." + id;
        Map<String, ?> spec = asMap(raw, where);
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
        double costGrowth = 0;
        if (spec.get("cost-growth") != null) {
            costGrowth = number(spec.get("cost-growth"), where + ".cost-growth");
            if (costGrowth < 1) {
                throw new EconomyConfigException(where + ".cost-growth must be >= 1, got " + costGrowth);
            }
        }
        int maxLevel = maxLevel(spec.get("max-level"), costGrowth, where);
        if (type == EffectType.RECIPES_UNLOCK && costGrowth > 0) {
            throw new EconomyConfigException(where + ": recipes-unlock is one-time (no cost-growth)");
        }
        double cost = number(spec.get("cost"), where + ".cost");
        if (cost <= 0) {
            throw new EconomyConfigException(where + ".cost must be > 0");
        }
        // recipes-unlock has no value: it switches the Recipe Book on
        double value = type == EffectType.RECIPES_UNLOCK && effect.get("value") == null
                ? 0 : number(effect.get("value"), where + ".effect.value");
        return new UpgradeDef(id, cost, type, value, target, unlockBuilding, unlockOwned, unlockBaked, costGrowth,
                maxLevel);
    }

    /** {@code produces: { ingredient: <id>, per-second: <rate> }}; absent = no ingredient. */
    private static @Nullable IngredientOutput produces(@Nullable Object raw, String where) throws EconomyConfigException {
        if (raw == null) {
            return null;
        }
        Map<String, ?> spec = asMap(raw, where);
        return new IngredientOutput(id(spec.get("ingredient"), where + ".ingredient"),
                positive(spec.get("per-second"), where + ".per-second"));
    }

    /**
     * {@code recipes:} (optional) in upgrades.yml. Every ingredient must be produced by a building (else the recipe
     * could never be bought), recipe ids must differ from upgrade ids (bought recipes share the upgrade levels), and
     * recipes need a {@code recipes-unlock} upgrade to ever show.
     */
    private static Map<String, RecipeDef> recipes(Map<String, ?> upgradesYaml, Map<String, BuildingDef> buildings,
                                                  Map<String, UpgradeDef> upgrades) throws EconomyConfigException {
        Map<String, RecipeDef> recipes = new LinkedHashMap<>();
        java.util.Set<String> produced = new java.util.HashSet<>();
        buildings.values().forEach(b -> {
            if (b.produces() != null) {
                produced.add(b.produces().ingredient());
            }
        });
        Map<String, ?> section = optionalMap(upgradesYaml.get("recipes"), "upgrades.yml: recipes");
        if (!section.isEmpty() && upgrades.values().stream().noneMatch(u -> u.type() == EffectType.RECIPES_UNLOCK)) {
            throw new EconomyConfigException("upgrades.yml: recipes need an upgrade with effect type recipes-unlock");
        }
        for (Map.Entry<String, ?> e : section.entrySet()) {
            String id = e.getKey();
            String where = "upgrades.yml: recipes." + id;
            if (upgrades.containsKey(id)) {
                throw new EconomyConfigException(where + " has the same id as an upgrade");
            }
            Map<String, ?> spec = asMap(e.getValue(), where);
            double cost = number(spec.get("cost"), where + ".cost");
            if (cost <= 0) {
                throw new EconomyConfigException(where + ".cost must be > 0");
            }
            Map<String, Double> ingredients = new LinkedHashMap<>();
            for (Map.Entry<String, ?> i : optionalMap(spec.get("ingredients"), where + ".ingredients").entrySet()) {
                if (!produced.contains(i.getKey())) {
                    throw new EconomyConfigException(where + ".ingredients." + i.getKey()
                            + " is not produced by any building");
                }
                ingredients.put(i.getKey(), positive(i.getValue(), where + ".ingredients." + i.getKey()));
            }
            if (ingredients.isEmpty()) {
                throw new EconomyConfigException(where + ".ingredients needs at least one ingredient");
            }
            Map<String, ?> effect = section(spec, "effect", where);
            if (!"cps-percent".equals(String.valueOf(effect.get("type")))) {
                throw new EconomyConfigException(where + ".effect.type must be cps-percent, got " + effect.get("type"));
            }
            Map<String, ?> unlock = optionalMap(spec.get("unlock"), where + ".unlock");
            recipes.put(id, new RecipeDef(id, cost, ingredients, positive(effect.get("value"), where + ".effect.value"),
                    unlock.containsKey("baked") ? number(unlock.get("baked"), where + ".unlock.baked") : 0));
        }
        return recipes;
    }

    private static String id(@Nullable Object raw, String where) throws EconomyConfigException {
        if (!(raw instanceof String text) || !text.matches("[a-z][a-z0-9_]*")) {
            throw new EconomyConfigException(where + " must be an id ([a-z][a-z0-9_]*), got " + raw);
        }
        return text;
    }

    private static double positive(@Nullable Object raw, String where) throws EconomyConfigException {
        double v = number(raw, where);
        if (v <= 0) {
            throw new EconomyConfigException(where + " must be > 0, got " + raw);
        }
        return v;
    }

    /** One-time upgrades have level 1; repeatable ones (with cost-growth) are unbounded unless max-level is set. */
    private static int maxLevel(@Nullable Object rawMax, double costGrowth, String where) throws EconomyConfigException {
        if (rawMax == null) {
            return costGrowth > 0 ? Integer.MAX_VALUE : 1;
        }
        if (costGrowth == 0) {
            throw new EconomyConfigException(where + ".max-level needs cost-growth (only repeatable upgrades have levels)");
        }
        if (!(rawMax instanceof Integer || rawMax instanceof Long) || ((Number) rawMax).longValue() < 1
                || ((Number) rawMax).longValue() > Integer.MAX_VALUE) {
            throw new EconomyConfigException(where + ".max-level must be an integer >= 1, got " + rawMax);
        }
        return ((Number) rawMax).intValue();
    }

    /**
     * {@code milestones:} = a list of owned counts (integers >= 1), each doubling production once reached.
     * Absent = no milestones. Sorted and de-duplicated, so the order in the file doesn't matter.
     */
    private static List<Integer> milestones(Object raw, String where) throws EconomyConfigException {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new EconomyConfigException(where + " must be a list of owned counts, got " + raw);
        }
        java.util.TreeSet<Integer> counts = new java.util.TreeSet<>();
        for (Object o : list) {
            if (!(o instanceof Integer || o instanceof Long) || ((Number) o).longValue() < 1
                    || ((Number) o).longValue() > Integer.MAX_VALUE) {
                throw new EconomyConfigException(where + " entries must be integers >= 1, got " + o);
            }
            counts.add(((Number) o).intValue());
        }
        return List.copyOf(counts);
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
