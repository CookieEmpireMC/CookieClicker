package dev.casperrs.cookieClicker.economy;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * The economy maths from {@code docs/balance/model.md}, plus the crispy crit from the bakery-core
 * contract. Pure Java (no Bukkit), so it can be tested against {@code docs/balance/golden-values.json}.
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

    // ---- costs ---------------------------------------------------------------------------------

    /** {@code ceil(base_cost × cost_growth ^ owned)}; +infinity for an unknown building. */
    public double nextCost(CookiePouch pouch, String buildingId) {
        return bulkCost(buildingId, pouch.owned(buildingId), 1);
    }

    /** Sum of the next {@code count} single costs starting at {@code owned}, each rounded up separately. */
    public double bulkCost(String buildingId, int owned, int count) {
        BuildingDef b = config.buildings().get(buildingId);
        if (b == null) {
            return Double.POSITIVE_INFINITY;
        }
        double total = 0;
        for (int i = 0; i < count; i++) {
            total += Math.ceil(b.baseCost() * Math.pow(config.costGrowth(), (double) owned + i));
        }
        return total;
    }

    // ---- derived values ------------------------------------------------------------------------

    /** {@code owned × base_cps × Π(bought building-multiplier for b)}; excludes global multipliers. */
    public double buildingCps(CookiePouch pouch, String buildingId) {
        BuildingDef b = config.buildings().get(buildingId);
        int owned = pouch.owned(buildingId);
        if (b == null || owned == 0) {
            return 0;
        }
        double mult = 1;
        for (String uid : pouch.upgradesView()) {
            UpgradeDef u = config.upgrades().get(uid);
            if (u != null && u.type() == EffectType.BUILDING_MULTIPLIER && buildingId.equals(u.building())) {
                mult *= u.value();
            }
        }
        return SafeMath.sanitize(owned * b.baseCps() * mult);
    }

    /**
     * What one more of {@code buildingId} would add to {@link #totalCps}:
     * {@code base_cps × Π(bought building-multiplier for b) × Π(bought global-multiplier)}. For the shop panel.
     */
    public double nextBuildingCps(CookiePouch pouch, String buildingId) {
        BuildingDef b = config.buildings().get(buildingId);
        if (b == null) {
            return 0;
        }
        double mult = 1;
        for (String uid : pouch.upgradesView()) {
            UpgradeDef u = config.upgrades().get(uid);
            if (u != null && u.type() == EffectType.BUILDING_MULTIPLIER && buildingId.equals(u.building())) {
                mult *= u.value();
            }
        }
        return SafeMath.sanitize(b.baseCps() * mult * product(pouch, EffectType.GLOBAL_MULTIPLIER));
    }

    /** {@code Σ building_cps × Π(bought global-multiplier)}. */
    public double totalCps(CookiePouch pouch) {
        double total = 0;
        for (String bid : pouch.ownedView().keySet()) {
            total += buildingCps(pouch, bid);
        }
        return SafeMath.sanitize(total * product(pouch, EffectType.GLOBAL_MULTIPLIER));
    }

    /** {@code (base_power + Σ click-add) × Π(click-multiplier) + Σ(click-cps-fraction) × total_cps}, before crits. */
    public double clickPower(CookiePouch pouch) {
        double add = 0;
        double fraction = 0;
        for (String uid : pouch.upgradesView()) {
            UpgradeDef u = config.upgrades().get(uid);
            if (u == null) {
                continue;
            }
            if (u.type() == EffectType.CLICK_ADD) {
                add += u.value();
            } else if (u.type() == EffectType.CLICK_CPS_FRACTION) {
                fraction += u.value();
            }
        }
        double power = (config.basePower() + add) * product(pouch, EffectType.CLICK_MULTIPLIER);
        if (fraction != 0) {
            power += fraction * totalCps(pouch);
        }
        return SafeMath.sanitize(power);
    }

    private double product(CookiePouch pouch, EffectType type) {
        double p = 1;
        for (String uid : pouch.upgradesView()) {
            UpgradeDef u = config.upgrades().get(uid);
            if (u != null && u.type() == type) {
                p *= u.value();
            }
        }
        return p;
    }

    // ---- actions -------------------------------------------------------------------------------

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

    /** Unlocked and not bought yet. */
    public boolean upgradeUnlocked(CookiePouch pouch, String upgradeId) {
        UpgradeDef u = config.upgrades().get(upgradeId);
        if (u == null || pouch.hasUpgrade(upgradeId) || pouch.baked() < u.unlockBaked()) {
            return false;
        }
        return u.unlockBuilding() == null || pouch.owned(u.unlockBuilding()) >= u.unlockOwned();
    }

    public boolean canAfford(CookiePouch pouch, String buildingId) {
        return buildingUnlocked(pouch, buildingId) && pouch.pouch() >= nextCost(pouch, buildingId);
    }

    public PurchaseResult buyBuilding(CookiePouch pouch, String buildingId) {
        if (!config.buildings().containsKey(buildingId)) {
            return PurchaseResult.UNKNOWN;
        }
        if (!buildingUnlocked(pouch, buildingId)) {
            return PurchaseResult.LOCKED;
        }
        double cost = nextCost(pouch, buildingId);
        if (pouch.pouch() < cost) {
            return PurchaseResult.CANT_AFFORD;
        }
        pouch.spend(cost);
        pouch.addBuilding(buildingId, 1);
        return PurchaseResult.BOUGHT;
    }

    public PurchaseResult buyUpgrade(CookiePouch pouch, String upgradeId) {
        UpgradeDef u = config.upgrades().get(upgradeId);
        if (u == null) {
            return PurchaseResult.UNKNOWN;
        }
        if (pouch.hasUpgrade(upgradeId)) {
            return PurchaseResult.ALREADY_OWNED;
        }
        if (!upgradeUnlocked(pouch, upgradeId)) {
            return PurchaseResult.LOCKED;
        }
        if (pouch.pouch() < u.cost()) {
            return PurchaseResult.CANT_AFFORD;
        }
        pouch.spend(u.cost());
        pouch.addUpgrade(upgradeId);
        return PurchaseResult.BOUGHT;
    }
}
