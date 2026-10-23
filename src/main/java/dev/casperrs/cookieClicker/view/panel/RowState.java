package dev.casperrs.cookieClicker.view.panel;

import dev.casperrs.cookieClicker.economy.BuildingDef;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;

/**
 * What one buildings panel row shows for the chosen bulk mode. The panel re-renders a row only when this changes
 * (cookies tick every second, but a row only changes when affordability flips or owned/count/cost/CPS change).
 * Pure Java. cost and cps are for {@code count} more; for Max, count is the most affordable now (1 when none is,
 * shown unaffordable); needed is the baked cookies to unlock.
 */
public record RowState(Style style, int owned, double cost, double cps, double needed, int count)
        implements PanelRowState {

    public enum Style {
        AFFORDABLE, UNAFFORDABLE, LOCKED;

        /** Key of the row background colour and of {@code panel.row.*}. */
        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The row template in {@code messages.yml}. */
        public String messagePath() {
            return switch (this) {
                case AFFORDABLE -> "panel.row.affordable";
                case UNAFFORDABLE -> "panel.row.unaffordable";
                case LOCKED -> "panel.row.locked";
            };
        }
    }

    /** A ×1 row. */
    public RowState(Style style, int owned, double cost, double cps, double needed) {
        this(style, owned, cost, cps, needed, 1);
    }

    /** The ×1 row. */
    public static RowState of(Economy economy, CookiePouch pouch, String buildingId) {
        return of(economy, pouch, buildingId, BulkMode.ONE);
    }

    public static RowState of(Economy economy, CookiePouch pouch, String buildingId, BulkMode mode) {
        BuildingDef def = economy.config().buildings().get(buildingId);
        double needed = def == null ? 0 : def.unlockBaked();
        boolean unlocked = economy.buildingUnlocked(pouch, buildingId);
        int count;
        boolean affordable;
        double cost;
        if (mode.max()) {
            int n = economy.maxAffordableBuildings(pouch, buildingId);
            count = Math.max(1, n);
            cost = economy.buildingBulkCost(pouch, buildingId, count);
            affordable = n >= 1;
        } else {
            count = mode.count();
            cost = economy.buildingBulkCost(pouch, buildingId, count);
            affordable = pouch.pouch() >= cost;
        }
        Style style = !unlocked ? Style.LOCKED : affordable ? Style.AFFORDABLE : Style.UNAFFORDABLE;
        return new RowState(style, pouch.owned(buildingId), cost, economy.buildingCpsGain(pouch, buildingId, count),
                needed, count);
    }

    @Override
    public double gain() {
        return cps;
    }

    @Override
    public String colorKey() {
        return style.key();
    }

    @Override
    public boolean bright() {
        return style == Style.AFFORDABLE;
    }

    @Override
    public boolean rolls() {
        return style != Style.LOCKED;
    }
}
