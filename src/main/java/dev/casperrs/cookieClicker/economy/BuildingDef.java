package dev.casperrs.cookieClicker.economy;

import java.util.List;

/**
 * One building from {@code buildings.yml}. Display names live in {@code messages.yml}. Purchasable once
 * {@code baked >= unlockBaked}; production doubles at each milestone count (ascending, distinct, may be empty).
 */
public record BuildingDef(String id, double baseCost, double baseCps, double unlockBaked, List<Integer> milestones) {

    public BuildingDef {
        milestones = List.copyOf(milestones);
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
}
