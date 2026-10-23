package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/** The admin outline, the optional "hide others near the pedestal" and the bought buildings. */
public record PlotSettings(Show show, HideOthers hideOthers, BuildingVisualSettings buildings) {

    /**
     * {@code /bakery plot show}.
     *
     * @param particleStep blocks between outline points (grows automatically past {@code maxPoints})
     * @param maxPoints    at least 12: a cuboid outline keeps one point per edge
     * @param plotEffect   effects.yml preset played at every plot outline point
     * @param houseEffect  effects.yml preset played at every house outline point
     */
    public record Show(double particleStep, int durationSeconds, int redrawTicks, int maxPoints, String plotEffect,
                       String houseEffect) {
    }

    /**
     * Players within {@code radius} of a pedestal don't see other players within {@code radius} of the same
     * pedestal.
     */
    public record HideOthers(boolean enabled, double radius, int checkTicks) {
    }

    private static final Show SHOW = new Show(1.0, 15, 20, 120, "plot_outline", "house_outline");
    private static final int HIDE_CHECK_TICKS = 10;

    public static PlotSettings from(ConfigurationSection c, Consumer<String> problems) {
        HideOthers hide = new HideOthers(
                c.getBoolean("plot.hide-others-near-pedestal.enabled", false),
                Math.clamp(c.getDouble("plot.hide-others-near-pedestal.radius", 6.0), 1.0, 64.0),
                HIDE_CHECK_TICKS);
        return new PlotSettings(SHOW, hide,
                BuildingVisualSettings.from(c.getConfigurationSection("plot.buildings"), problems));
    }
}
