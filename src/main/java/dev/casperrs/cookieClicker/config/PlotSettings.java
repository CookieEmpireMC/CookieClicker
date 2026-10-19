package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/** {@code config.yml} {@code plot.*}: the admin outline and the optional "hide others near the pedestal". */
public record PlotSettings(Show show, HideOthers hideOthers, int chimneyReach) {

    /**
     * {@code /bakery plot show}.
     *
     * @param particleStep blocks between outline points (grows automatically past {@code maxPoints})
     * @param redrawTicks  the outline is replayed this often while it shows
     * @param plotEffect   effects.yml preset played at every plot outline point
     * @param houseEffect  effects.yml preset played at every house outline point
     */
    public record Show(double particleStep, int durationSeconds, int redrawTicks, int maxPoints, String plotEffect,
                       String houseEffect) {
    }

    /**
     * Players standing within {@code radius} of a pedestal don't see other players who are within {@code radius} of
     * the same pedestal (less clutter around everyone's own cookie). Off by default.
     */
    public record HideOthers(boolean enabled, double radius, int checkTicks) {
    }

    public static PlotSettings from(ConfigurationSection c, Consumer<String> problems) {
        Show show = new Show(
                Math.clamp(c.getDouble("plot.show.particle-step", 1.0), 0.25, 16.0),
                Math.clamp(c.getInt("plot.show.duration-seconds", 15), 1, 600),
                Math.clamp(c.getInt("plot.show.redraw-ticks", 20), 2, 200),
                // a cuboid outline has at least 12 points (one per edge), so fewer can't be honoured
                Math.clamp(c.getInt("plot.show.max-points", 120), 12, 2000),
                effectId(c, "plot.show.plot-effect", "plot_outline", problems),
                effectId(c, "plot.show.house-effect", "house_outline", problems));
        HideOthers hide = new HideOthers(
                c.getBoolean("plot.hide-others-near-pedestal.enabled", false),
                Math.clamp(c.getDouble("plot.hide-others-near-pedestal.radius", 6.0), 1.0, 64.0),
                Math.clamp(c.getInt("plot.hide-others-near-pedestal.check-ticks", 10), 1, 200));
        // /bakery plot setchimney: how far away the looked-at chimney top may be
        return new PlotSettings(show, hide, Math.clamp(c.getInt("plot.chimney-reach", 16), 1, 64));
    }

    private static String effectId(ConfigurationSection c, String path, String fallback, Consumer<String> problems) {
        String id = c.getString(path, fallback);
        if (id == null || !id.matches("[a-z0-9_]+")) {
            problems.accept("config.yml: " + path + " must be an effects.yml id; using " + fallback);
            return fallback;
        }
        return id;
    }
}
