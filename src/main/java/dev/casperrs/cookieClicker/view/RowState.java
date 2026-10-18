package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.economy.BuildingDef;
import dev.casperrs.cookieClicker.economy.CookiePouch;
import dev.casperrs.cookieClicker.economy.Economy;

/**
 * What one shop row shows. The panel re-renders a row only when this changes (cookies tick every second,
 * but a row only changes when affordability flips or owned/cost/CPS change). Pure Java.
 *
 * @param needed baked cookies needed to unlock (shown on locked rows; static per building)
 */
public record RowState(Style style, int owned, double cost, double cps, double needed) {

    public enum Style {
        AFFORDABLE, UNAFFORDABLE, LOCKED;

        /** Key under {@code station.panel.colors} and {@code panel.row.*}. */
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

    public static RowState of(Economy economy, CookiePouch pouch, String buildingId) {
        BuildingDef def = economy.config().buildings().get(buildingId);
        double needed = def == null ? 0 : def.unlockBaked();
        Style style = !economy.buildingUnlocked(pouch, buildingId) ? Style.LOCKED
                : economy.canAfford(pouch, buildingId) ? Style.AFFORDABLE : Style.UNAFFORDABLE;
        return new RowState(style, pouch.owned(buildingId), economy.nextCost(pouch, buildingId),
                economy.nextBuildingCps(pouch, buildingId), needed);
    }
}
