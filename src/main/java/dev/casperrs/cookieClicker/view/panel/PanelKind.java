package dev.casperrs.cookieClicker.view.panel;

import java.util.Locale;

/** The two hologram panels at a station: buildings (automated income) and click upgrades. */
public enum PanelKind {
    BUILDINGS, CLICKS;

    /** Key under {@code station.panels.*}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
