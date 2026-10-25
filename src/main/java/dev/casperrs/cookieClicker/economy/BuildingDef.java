package dev.casperrs.cookieClicker.economy;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * One building from {@code buildings.yml}. Display names live in {@code messages.yml}. Purchasable once
 * {@code baked >= unlockBaked}; production doubles at each milestone count (ascending, distinct, may be empty).
 * produces is null for a building without an ingredient.
 */
public record BuildingDef(String id, double baseCost, double baseCps, double unlockBaked, List<Integer> milestones,
                          @Nullable IngredientOutput produces) {

    public BuildingDef {
        milestones = List.copyOf(milestones);
    }

    /** Without an ingredient. */
    public BuildingDef(String id, double baseCost, double baseCps, double unlockBaked, List<Integer> milestones) {
        this(id, baseCost, baseCps, unlockBaked, milestones, null);
    }

    /** Without milestones. */
    public BuildingDef(String id, double baseCost, double baseCps, double unlockBaked) {
        this(id, baseCost, baseCps, unlockBaked, List.of());
    }

    /** How many milestones {@code owned} buildings have reached ({@code owned >= m}). */
    public int milestonesReached(int owned) {
        int n = 0;
        for (int m : milestones) {
            if (owned >= m) {
                n++;
            }
        }
        return n;
    }

    /** {@code 2 ^ milestonesReached(owned)}: the milestone factor on this building's production. */
    public double milestoneMultiplier(int owned) {
        return Math.scalb(1.0, milestonesReached(owned));
    }

    /** {@code base_cps × owned × 2^(milestones reached)}, before upgrade multipliers. */
    public double production(int owned) {
        return owned <= 0 ? 0 : owned * baseCps * milestoneMultiplier(owned);
    }

    /** {@code owned × per_second × 2^(milestones reached)} units of {@link #produces}; 0 without an ingredient. */
    public double ingredientProduction(int owned) {
        return owned <= 0 || produces == null ? 0 : owned * produces.perSecond() * milestoneMultiplier(owned);
    }
}
