package dev.casperrs.cookieClicker.view;

import dev.casperrs.cookieClicker.view.panel.PanelKind;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * What one of our {@code Interaction} hitboxes is, of one owner at one station: the cookie, a hologram panel (one
 * box per panel; which row or toggle label was meant is resolved from the eye ray), or an upgrade icon on the shelf.
 * panel is set only for PANEL boxes, upgradeId only for SHELF icons.
 */
public record ClickTarget(UUID owner, String stationId, Kind kind, @Nullable PanelKind panel,
                          @Nullable String upgradeId) {

    public enum Kind { COOKIE, PANEL, SHELF }

    public static ClickTarget cookie(UUID owner, String stationId) {
        return new ClickTarget(owner, stationId, Kind.COOKIE, null, null);
    }

    public static ClickTarget panel(UUID owner, String stationId, PanelKind panel) {
        return new ClickTarget(owner, stationId, Kind.PANEL, panel, null);
    }

    public static ClickTarget shelf(UUID owner, String stationId, String upgradeId) {
        return new ClickTarget(owner, stationId, Kind.SHELF, null, upgradeId);
    }
}
