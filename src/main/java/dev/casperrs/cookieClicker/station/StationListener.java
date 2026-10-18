package dev.casperrs.cookieClicker.station;

/**
 * Notified on the main thread when stations appear or disappear (command, reload, enable/disable).
 * Pass 2 hooks the station visuals in here: spawn on add, despawn on remove.
 */
public interface StationListener {

    void onStationAdded(Station station);

    void onStationRemoved(Station station);
}
