package dev.casperrs.cookieClicker.view;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * What one of our {@code Interaction} hitboxes is: the cookie or a shop row, of one owner at one station.
 *
 * @param buildingId the row's building; null for the cookie
 */
public record ClickTarget(UUID owner, String stationId, Kind kind, @Nullable String buildingId) {

    public enum Kind { COOKIE, ROW }
}
