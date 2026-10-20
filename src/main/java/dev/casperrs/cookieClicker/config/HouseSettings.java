package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code config.yml} {@code house.*}: design limits, the first-visit build cinematic, the chimney smoke, factory steam and
 * the ambient oven hum.
 *
 * @param maxBlocks largest design box (x * y * z) {@code /bakery house place} accepts; also the largest house region
 *                  box the cinematic will hide
 */
public record HouseSettings(int maxBlocks, Cinematic cinematic, Chimney chimney, Steam steam, Ambient ambient) {

    public enum Trigger { ENTER_PLOT, VIEWPOINT }

    public enum KeepOut { HOLD, SLOW, NONE }

    /**
     * @param trigger             {@code enter-plot}: start when entering the plot outside the house; {@code viewpoint}:
     *                            after being moved to the viewpoint
     * @param teleportToViewpoint move the player to the plot's viewpoint when the cinematic starts
     * @param blocksPerTick       blocks started per tick (fractions allowed: 0.5 = one every other tick)
     * @param maxFallingDisplays  falling block displays alive per player (at most 24, the effects engine's cap)
     * @param layerDelayTicks     pause before the first layer and after each layer has landed
     * @param keepOut             how the player is kept out of the invisible house
     * @param slowAmount          {@code keep-out: slow}: movement speed modifier (-1 = stand still, 0 = normal)
     * @param fakeAirPerTick      fake-air blocks sent per tick when hiding the house or re-asserting it
     * @param refreshHiddenTicks  re-send fake air for still-hidden blocks this often (0 = off)
     * @param reassertRadius      after an interaction within this many blocks of the house, re-send fake air
     * @param hammerEvery         play {@code house_hammer} on every n-th landing (0 = never)
     * @param viewpointRadius     {@code trigger: viewpoint}: how close (blocks) to the viewpoint a teleport must end
     * @param confettiHeight      blocks above the top of the house region where the confetti bursts
     */
    public record Cinematic(boolean enabled, Trigger trigger, boolean teleportToViewpoint, double blocksPerTick,
                            int maxFallingDisplays, int layerDelayTicks, KeepOut keepOut, double slowAmount,
                            int fakeAirPerTick, int refreshHiddenTicks, int reassertRadius, int hammerEvery,
                            double viewpointRadius, double confettiHeight) {
    }

    /**
     * @param smoke        play the chimney smoke loop for players in the plot
     * @param crackleEvery replay {@code house_chimney_crackle} every n-th smoke play (0 = never)
     */
    public record Chimney(boolean smoke, int crackleEvery) {
    }

    /**
     * @param enabled play {@code factory_steam} at every steam point for players in the plot
     * @param every   ticks between the start of one vent's loop and the next (so vents don't puff together)
     */
    public record Steam(boolean enabled, int every) {
    }

    /**
     * @param enabled play {@code factory_ambient} for players in the plot
     * @param every   ticks between plays (the sound is a 4 s loop: 80)
     * @param radius  only for players within this many blocks of the first steam point (or the house centre)
     */
    public record Ambient(boolean enabled, int every, double radius) {
    }

    public static HouseSettings from(ConfigurationSection c, Consumer<String> problems) {
        String p = "house.cinematic.";
        Cinematic cinematic = new Cinematic(
                c.getBoolean(p + "enabled", true),
                parse(Trigger.class, c.getString(p + "trigger", "enter-plot"), Trigger.ENTER_PLOT, p + "trigger", problems),
                c.getBoolean(p + "teleport-to-viewpoint", false),
                Math.clamp(c.getDouble(p + "blocks-per-tick", 2.25), 0.05, 64.0),
                Math.clamp(c.getInt(p + "max-falling-displays", 24), 1, 24),
                Math.clamp(c.getInt(p + "layer-delay-ticks", 5), 0, 200),
                parse(KeepOut.class, c.getString(p + "keep-out", "hold"), KeepOut.HOLD, p + "keep-out", problems),
                Math.clamp(c.getDouble(p + "slow-amount", -0.7), -1.0, 0.0),
                Math.clamp(c.getInt(p + "fake-air-per-tick", 4096), 64, 65536),
                Math.clamp(c.getInt(p + "refresh-hidden-ticks", 40), 0, 1200),
                Math.clamp(c.getInt(p + "reassert-radius", 2), 0, 16),
                Math.clamp(c.getInt(p + "hammer-every", 3), 0, 1000),
                Math.clamp(c.getDouble(p + "viewpoint-radius", 2.0), 0.5, 16.0),
                Math.clamp(c.getDouble(p + "confetti-height", 1.0), -16.0, 16.0));
        Chimney chimney = new Chimney(
                c.getBoolean("house.chimney.smoke", true),
                Math.clamp(c.getInt("house.chimney.crackle-every", 6), 0, 1000));
        Steam steam = new Steam(
                c.getBoolean("house.steam.enabled", true),
                Math.clamp(c.getInt("house.steam.every", 7), 0, 200));
        Ambient ambient = new Ambient(
                c.getBoolean("house.ambient.enabled", true),
                Math.clamp(c.getInt("house.ambient.every", 80), 10, 6000),
                Math.clamp(c.getDouble("house.ambient.radius", 24.0), 1.0, 128.0));
        return new HouseSettings(Math.clamp(c.getInt("house.max-blocks", 40000), 1, 1_000_000), cinematic, chimney,
                steam, ambient);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback, String path,
                                               Consumer<String> problems) {
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            problems.accept("config.yml: " + path + " '" + raw + "' is not one of "
                    + java.util.Arrays.toString(type.getEnumConstants()).toLowerCase(Locale.ROOT).replace('_', '-')
                    + "; using " + fallback.name().toLowerCase(Locale.ROOT).replace('_', '-'));
            return fallback;
        }
    }
}
