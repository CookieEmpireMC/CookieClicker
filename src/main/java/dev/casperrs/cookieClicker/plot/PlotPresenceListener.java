package dev.casperrs.cookieClicker.plot;

import org.bukkit.entity.Player;

/**
 * Told by {@link PlotTracker} (main thread) when a player enters or leaves a plot or its house region. The
 * starter-house cinematic (pass 2) hooks in here.
 *
 * <p>On a change the order is: leave house, leave plot, enter plot, enter house. Only plots in {@code plots.yml} are
 * reported. Quit fires the leave calls before the player is forgotten; reload re-checks everyone and fires the
 * difference; disable fires nothing (owners clean up in their own shutdown).
 */
public interface PlotPresenceListener {

    default void onEnterPlot(Player player, String plotId) {
    }

    default void onLeavePlot(Player player, String plotId) {
    }

    default void onEnterHouse(Player player, String plotId) {
    }

    default void onLeaveHouse(Player player, String plotId) {
    }
}
