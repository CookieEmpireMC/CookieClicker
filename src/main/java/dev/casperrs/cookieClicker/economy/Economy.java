package dev.casperrs.cookieClicker.economy;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * The economy maths from {@code docs/balance/model.md}, plus the crispy crit from the bakery-core
 * contract. Pure Java (no Bukkit), so it can be tested against {@code docs/balance/golden-values.json}.
 *
 * <p>Upgrades have levels: one-time upgrades are 0 or 1, repeatable ones (with {@code cost-growth}) go
 * up to {@code max-level}. Effects apply at the upgrade's level ({@link UpgradeDef#effectAt}).
 * Bulk buys use {@link BulkMath} (geometric sum rounded up once, closed-form Max).
 *
 * <p>Holds the current {@link EconomyConfig}; {@link #setConfig} swaps it on reload. Call from the
 * main thread only, like {@link CookiePouch}.
 */
public final class Economy {

    private final RandomGenerator random;
    private EconomyConfig config;

    /** @param random source for crit rolls; inject a seeded/fixed one in tests */
    public Economy(EconomyConfig config, RandomGenerator random) {
        this.config = Objects.requireNonNull(config);
        this.random = Objects.requireNonNull(random);
    }

    public EconomyConfig config() {
        return config;
    }

    public void setConfig(EconomyConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    /** {@code ceil(base_cost × cost_growth ^ owned)}; +infinity for an unknown building. */
    public double nextCost(CookiePouch pouch, String buildingId) {
        return bulkCost(buildingId, pouch.owned(buildingId), 1);
    }

    /**
     * {@code bulk_cost} of {@code count} buildings when {@code owned} are owned: the geometric sum,
     * rounded up <b>once</b> for the batch. 0 for {@code count <= 0}; +infinity for an unknown building.
     */
    public double bulkCost(String buildingId, int owned, int count) {
        BuildingDef b = config.buildings().get(buildingId);
        if (b == null) {
            return Double.POSITIVE_INFINITY;
        }
        return BulkMath.bulkCost(b.baseCost(), config.costGrowth(), owned, count);
    }

    /** What the next {@code count} of {@code buildingId} cost this player right now (for ×N rows). */
    public double buildingBulkCost(CookiePouch pouch, String buildingId, int count) {
        return bulkCost(buildingId, pouch.owned(buildingId), count);
    }

    /**
     * Max count for a building: the most this player can afford right now, at most {@link BulkMath#MAX_BULK}.
     * 0 when locked, unknown or not even one is affordable. Closed form, no loop over counts.
     */
    public int maxAffordableBuildings(CookiePouch pouch, String buildingId) {
        BuildingDef b = config.buildings().get(buildingId);
        if (b == null || !buildingUnlocked(pouch, buildingId)) {
            return 0;
        }
        return BulkMath.maxAffordable(b.baseCost(), config.costGrowth(), pouch.owned(buildingId),
                BulkMath.MAX_BULK, pouch.pouch());
    }

    /** The upgrade's level for this player (0 = not bought). */
    public int upgradeLevel(CookiePouch pouch, String upgradeId) {
        return pouch.upgradeLevel(upgradeId);
    }

    /** Cost of the next level (one-time: {@code cost}); +infinity when maxed or unknown. */
    public double upgradeCost(CookiePouch pouch, String upgradeId) {
        return upgradeBulkCost(upgradeId, pouch.upgradeLevel(upgradeId), 1);
    }

    /**
     * Cost of the next {@code count} levels from {@code level}, like {@code upgrade_bulk_cost} in the
     * simulator: {@code count} is first cut to the levels left below the cap; one-time upgrades cost
     * {@code cost} (one level). +infinity when no level is left or the upgrade is unknown.
     */
    public double upgradeBulkCost(String upgradeId, int level, int count) {
        UpgradeDef u = config.upgrades().get(upgradeId);
        if (u == null) {
            return Double.POSITIVE_INFINITY;
        }
        int n = (int) Math.min(count, (long) u.maxLevel() - level);
        if (n <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return u.repeatable() ? BulkMath.bulkCost(u.cost(), u.costGrowth(), level, n) : u.cost();
    }

    /** {@link #upgradeBulkCost(String, int, int)} at this player's current level (for ×N rows). */
    public double upgradeBulkCost(CookiePouch pouch, String upgradeId, int count) {
        return upgradeBulkCost(upgradeId, pouch.upgradeLevel(upgradeId), count);
    }

    /**
     * Max count for an upgrade: repeatable = the most levels affordable now, capped by the levels left
     * below {@code max-level} and by {@link BulkMath#MAX_BULK}; one-time = 1 if not bought and affordable.
     * 0 when locked, maxed, unknown or unaffordable.
     */
    public int maxAffordableUpgrade(CookiePouch pouch, String upgradeId) {
        UpgradeDef u = config.upgrades().get(upgradeId);
        if (u == null || !upgradeUnlocked(pouch, upgradeId)) {
            return 0;
        }
        int level = pouch.upgradeLevel(upgradeId);
        if (!u.repeatable()) {
            return level < 1 && pouch.pouch() >= u.cost() ? 1 : 0;
        }
        int left = (int) Math.min(Integer.MAX_VALUE, (long) u.maxLevel() - level);
        return BulkMath.maxAffordable(u.cost(), u.costGrowth(), level, left, pouch.pouch());
    }

    /**
     * {@code base_cps × owned × 2^(milestones reached) × Π(building-multiplier for b at its level)};
     * excludes global multipliers.
     */
    public double buildingCps(CookiePouch pouch, String buildingId) {
        BuildingDef b = config.buildings().get(buildingId);
        int owned = pouch.owned(buildingId);
        if (b == null || owned == 0) {
            return 0;
        }
        return SafeMath.sanitize(b.production(owned) * buildingMultiplier(pouch, buildingId, null, 0));
    }

    /** What one more of {@code buildingId} would add to {@link #totalCps} (incl. crossing a milestone). */
    public double nextBuildingCps(CookiePouch pouch, String buildingId) {
        return buildingCpsGain(pouch, buildingId, 1);
    }

    /**
     * What {@code count} more of {@code buildingId} would add to {@link #totalCps}: the building's
     * production at {@code owned + count} minus at {@code owned} (milestones crossed included),
     * {@code × Π(building-multiplier for b) × Π(global-multiplier)}. For the shop panel.
     */
    public double buildingCpsGain(CookiePouch pouch, String buildingId, int count) {
        BuildingDef b = config.buildings().get(buildingId);
        if (b == null || count <= 0) {
            return 0;
        }
        int owned = pouch.owned(buildingId);
        int after = (int) Math.min(Integer.MAX_VALUE, (long) owned + count);
        double delta = b.production(after) - b.production(owned);
        return SafeMath.sanitize(delta * buildingMultiplier(pouch, buildingId, null, 0)
                * product(pouch, EffectType.GLOBAL_MULTIPLIER, null, 0));
    }

    /** {@code Σ building_cps × Π(global-multiplier)}. */
    public double totalCps(CookiePouch pouch) {
        return totalCps(pouch, null, 0);
    }

    /**
     * {@code (base_power + Σ click-add) × Π(click-multiplier) + Σ(click-cps-fraction) × total_cps},
     * every effect at its upgrade's level, before crits.
     */
    public double clickPower(CookiePouch pouch) {
        return clickPower(pouch, null, 0);
    }

    /**
     * How much {@link #clickPower} would grow if {@code upgradeId} gained {@code count} levels
     * (one-time upgrades: one level; cut at {@code max-level}), for the +CPC column. Covers every
     * effect type, since multipliers and CPS fractions change click power too. 0 when unknown or maxed.
     */
    public double clickPowerGain(CookiePouch pouch, String upgradeId, int count) {
        UpgradeDef u = config.upgrades().get(upgradeId);
        int level = pouch.upgradeLevel(upgradeId);
        if (u == null || count <= 0 || level >= u.maxLevel()) {
            return 0;
        }
        int after = (int) Math.min(u.maxLevel(), (long) level + (u.repeatable() ? count : 1));
        return SafeMath.sanitize(clickPower(pouch, upgradeId, after) - clickPower(pouch));
    }

    /** Level of {@code uid}, with {@code overrideId} treated as having {@code overrideLevel}. */
    private static int level(CookiePouch pouch, String uid, @Nullable String overrideId, int overrideLevel) {
        return uid.equals(overrideId) ? overrideLevel : pouch.upgradeLevel(uid);
    }

    /** Every upgrade ID with a level {@code >= 1}, plus the override (which may not be bought yet). */
    private static Iterable<String> levelled(CookiePouch pouch, @Nullable String overrideId) {
        if (overrideId == null || pouch.hasUpgrade(overrideId)) {
            return pouch.upgradesView();
        }
        List<String> ids = new ArrayList<>(pouch.upgradesView());
        ids.add(overrideId);
        return ids;
    }

    private double buildingMultiplier(CookiePouch pouch, String buildingId, @Nullable String overrideId, int overrideLevel) {
        double mult = 1;
        for (String uid : levelled(pouch, overrideId)) {
            UpgradeDef u = config.upgrades().get(uid);
            if (u != null && u.type() == EffectType.BUILDING_MULTIPLIER && buildingId.equals(u.building())) {
                mult *= u.effectAt(level(pouch, uid, overrideId, overrideLevel));
            }
        }
        return mult;
    }

    private double totalCps(CookiePouch pouch, @Nullable String overrideId, int overrideLevel) {
        double total = 0;
        for (Map.Entry<String, Integer> e : pouch.ownedView().entrySet()) {
            BuildingDef b = config.buildings().get(e.getKey());
            if (b == null || e.getValue() == 0) {
                continue;
            }
            total += SafeMath.sanitize(b.production(e.getValue())
                    * buildingMultiplier(pouch, e.getKey(), overrideId, overrideLevel));
        }
        return SafeMath.sanitize(total * product(pouch, EffectType.GLOBAL_MULTIPLIER, overrideId, overrideLevel));
    }

    private double clickPower(CookiePouch pouch, @Nullable String overrideId, int overrideLevel) {
        double add = 0;
        double fraction = 0;
        for (String uid : levelled(pouch, overrideId)) {
            UpgradeDef u = config.upgrades().get(uid);
            if (u == null) {
                continue;
            }
            int lv = level(pouch, uid, overrideId, overrideLevel);
            if (u.type() == EffectType.CLICK_ADD) {
                add += u.effectAt(lv);
            } else if (u.type() == EffectType.CLICK_CPS_FRACTION) {
                fraction += u.effectAt(lv);
            }
        }
        double power = (config.basePower() + add) * product(pouch, EffectType.CLICK_MULTIPLIER, overrideId, overrideLevel);
        if (fraction != 0) {
            power += fraction * totalCps(pouch, overrideId, overrideLevel);
        }
        return SafeMath.sanitize(power);
    }

    private double product(CookiePouch pouch, EffectType type, @Nullable String overrideId, int overrideLevel) {
        double p = 1;
        for (String uid : levelled(pouch, overrideId)) {
            UpgradeDef u = config.upgrades().get(uid);
            if (u != null && u.type() == type) {
                p *= u.effectAt(level(pouch, uid, overrideId, overrideLevel));
            }
        }
        return p;
    }

    /**
     * One click. Rolls a crit ({@code random.nextDouble() < crit-chance}); a crit multiplies the whole
     * click (including the CPS fraction) by {@code crit-multiplier}. Adds to pouch and baked.
     */
    public ClickResult click(CookiePouch pouch) {
        double power = clickPower(pouch);
        boolean crit = config.critChance() > 0 && random.nextDouble() < config.critChance();
        double amount = crit ? SafeMath.multiply(power, config.critMultiplier()) : power;
        pouch.earn(amount);
        return new ClickResult(amount, crit);
    }

    /** Production for {@code seconds} (the ticker calls this with 1 once per second). Returns the amount added. */
    public double produce(CookiePouch pouch, double seconds) {
        double amount = SafeMath.multiply(totalCps(pouch), seconds);
        pouch.earn(amount);
        return amount;
    }

    public boolean buildingUnlocked(CookiePouch pouch, String buildingId) {
        BuildingDef b = config.buildings().get(buildingId);
        return b != null && pouch.baked() >= b.unlockBaked();
    }

    /**
     * Available (model.md): the unlock condition holds and the level is below its cap
     * (one-time: not bought; repeatable: below {@code max-level}).
     */
    public boolean upgradeUnlocked(CookiePouch pouch, String upgradeId) {
        UpgradeDef u = config.upgrades().get(upgradeId);
        return u != null && pouch.upgradeLevel(upgradeId) < u.maxLevel() && upgradeConditionMet(pouch, upgradeId);
    }

    /** Only the {@code unlock} condition ({@code baked} / building owned), ignoring the level. */
    public boolean upgradeConditionMet(CookiePouch pouch, String upgradeId) {
        UpgradeDef u = config.upgrades().get(upgradeId);
        if (u == null || pouch.baked() < u.unlockBaked()) {
            return false;
        }
        return u.unlockBuilding() == null || pouch.owned(u.unlockBuilding()) >= u.unlockOwned();
    }

    public boolean canAfford(CookiePouch pouch, String buildingId) {
        return buildingUnlocked(pouch, buildingId) && pouch.pouch() >= nextCost(pouch, buildingId);
    }

    /** Buys one building (the ×1 shop); see {@link #buyBuildings}. */
    public PurchaseResult buyBuilding(CookiePouch pouch, String buildingId) {
        return buyBuildings(pouch, buildingId, 1).result();
    }

    /**
     * Buys exactly {@code count} of {@code buildingId}, all or nothing: on {@link PurchaseResult#BOUGHT}
     * the pouch paid {@code bulk_cost(…, count)} once and owns {@code count} more; on any other result
     * nothing changed. {@code count} must be in {@code 1..MAX_BULK} ({@link PurchaseResult#BAD_COUNT} otherwise).
     */
    public BulkPurchase buyBuildings(CookiePouch pouch, String buildingId, int count) {
        if (!config.buildings().containsKey(buildingId)) {
            return BulkPurchase.failed(PurchaseResult.UNKNOWN);
        }
        if (count < 1 || count > BulkMath.MAX_BULK) {
            return BulkPurchase.failed(PurchaseResult.BAD_COUNT);
        }
        if (!buildingUnlocked(pouch, buildingId)) {
            return BulkPurchase.failed(PurchaseResult.LOCKED);
        }
        double cost = buildingBulkCost(pouch, buildingId, count);
        if (!(pouch.pouch() >= cost)) {
            return BulkPurchase.failed(PurchaseResult.CANT_AFFORD);
        }
        pouch.spend(cost);
        pouch.addBuilding(buildingId, count);
        return new BulkPurchase(PurchaseResult.BOUGHT, count, cost);
    }

    /**
     * The Max button: buys {@link #maxAffordableBuildings} (at least 1). When not even one is
     * affordable, returns the reason a ×1 buy would fail ({@code CANT_AFFORD}, {@code LOCKED}, …).
     */
    public BulkPurchase buyMaxBuildings(CookiePouch pouch, String buildingId) {
        return buyBuildings(pouch, buildingId, Math.max(1, maxAffordableBuildings(pouch, buildingId)));
    }

    /** Buys one level of an upgrade (the ×1 shop); see {@link #buyUpgrades}. */
    public PurchaseResult buyUpgrade(CookiePouch pouch, String upgradeId) {
        return buyUpgrades(pouch, upgradeId, 1).result();
    }

    /**
     * Buys {@code count} levels of {@code upgradeId}, all or nothing. One-time upgrades are always
     * bought once whatever {@code count} says (cost = {@code cost}). A repeatable one succeeds only if
     * {@code level + count <= max-level} ({@link PurchaseResult#MAX_LEVEL} otherwise) and the pouch
     * holds {@code bulk_cost(…, count)}. On anything but {@code BOUGHT} nothing changed.
     */
    public BulkPurchase buyUpgrades(CookiePouch pouch, String upgradeId, int count) {
        UpgradeDef u = config.upgrades().get(upgradeId);
        if (u == null) {
            return BulkPurchase.failed(PurchaseResult.UNKNOWN);
        }
        if (count < 1 || count > BulkMath.MAX_BULK) {
            return BulkPurchase.failed(PurchaseResult.BAD_COUNT);
        }
        int level = pouch.upgradeLevel(upgradeId);
        if (!u.repeatable()) {
            if (level >= 1) {
                return BulkPurchase.failed(PurchaseResult.ALREADY_OWNED);
            }
            count = 1;
        } else if ((long) level + count > u.maxLevel()) {
            return BulkPurchase.failed(PurchaseResult.MAX_LEVEL);
        }
        if (!upgradeConditionMet(pouch, upgradeId)) {
            return BulkPurchase.failed(PurchaseResult.LOCKED);
        }
        double cost = upgradeBulkCost(upgradeId, level, count);
        if (!(pouch.pouch() >= cost)) {
            return BulkPurchase.failed(PurchaseResult.CANT_AFFORD);
        }
        pouch.spend(cost);
        pouch.addUpgradeLevels(upgradeId, count);
        return new BulkPurchase(PurchaseResult.BOUGHT, count, cost);
    }

    /**
     * The Max button for an upgrade: buys {@link #maxAffordableUpgrade} levels (at least 1); when none
     * is affordable, returns the reason a ×1 buy would fail.
     */
    public BulkPurchase buyMaxUpgrades(CookiePouch pouch, String upgradeId) {
        return buyUpgrades(pouch, upgradeId, Math.max(1, maxAffordableUpgrade(pouch, upgradeId)));
    }
}
