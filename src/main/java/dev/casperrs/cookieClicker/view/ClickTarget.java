package dev.casperrs.cookieClicker.view;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * What one of our {@code Interaction} hitboxes is, of one owner at one station: the cookie, a panel row slot, a
 * panel's ×N toggle, or an upgrade icon on the shelf.
 *
 * @param panel     the panel of a ROW or TOGGLE; null otherwise
 * @param row       the row slot index of a ROW (what it shows is looked up at click time); -1 otherwise
 * @param upgradeId the upgrade of a SHELF icon; null otherwise
 */
public record ClickTarget(UUID owner, String stationId, Kind kind, @Nullable PanelKind panel, int row,
                          @Nullable String upgradeId) {

    public enum Kind { COOKIE, ROW, TOGGLE, SHELF }

    public static ClickTarget cookie(UUID owner, String stationId) {
        return new ClickTarget(owner, stationId, Kind.COOKIE, null, -1, null);
    }

    public static ClickTarget row(UUID owner, String stationId, PanelKind panel, int row) {
        return new ClickTarget(owner, stationId, Kind.ROW, panel, row, null);
    }

    public static ClickTarget toggle(UUID owner, String stationId, PanelKind panel) {
        return new ClickTarget(owner, stationId, Kind.TOGGLE, panel, -1, null);
    }

    public static ClickTarget shelf(UUID owner, String stationId, String upgradeId) {
        return new ClickTarget(owner, stationId, Kind.SHELF, null, -1, upgradeId);
    }
}
