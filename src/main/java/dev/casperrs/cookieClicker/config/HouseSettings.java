package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code config.yml} {@code house.*}. {@link Cinematic} is read and validated now; the first-visit cinematic itself
 * (starter-house pass 2) uses it.
 *
 * @param maxBlocks largest design box (x * y * z) {@code /bakery house place} accepts
 */
public record HouseSettings(int maxBlocks, Cinematic cinematic) {

    public enum Trigger { ENTER_PLOT, VIEWPOINT }

    public enum KeepOut { HOLD, SLOW, NONE }

    /**
     * @param trigger             {@code enter-plot}: start when entering the plot outside the house; {@code viewpoint}:
     *                            after being moved to the viewpoint
     * @param teleportToViewpoint move the player to the plot's viewpoint when the cinematic starts
     * @param blocksPerTick       blocks revealed per tick
     * @param maxFallingDisplays  falling block displays alive per player
     * @param layerDelayTicks     pause between layers
     * @param keepOut             how the player is kept out of the invisible house
     * @param fakeAirPerTick      fake-air blocks sent per tick when hiding the house
     * @param refreshHiddenTicks  re-send fake air for still-hidden blocks this often (0 = off)
     * @param reassertRadius      after an interaction within this many blocks of the house, re-send fake air
     */
    public record Cinematic(boolean enabled, Trigger trigger, boolean teleportToViewpoint, int blocksPerTick,
                            int maxFallingDisplays, int layerDelayTicks, KeepOut keepOut, int fakeAirPerTick,
                            int refreshHiddenTicks, int reassertRadius) {
    }

    public static HouseSettings from(ConfigurationSection c, Consumer<String> problems) {
        String p = "house.cinematic.";
        Cinematic cinematic = new Cinematic(
                c.getBoolean(p + "enabled", true),
                parse(Trigger.class, c.getString(p + "trigger", "enter-plot"), Trigger.ENTER_PLOT, p + "trigger", problems),
                c.getBoolean(p + "teleport-to-viewpoint", false),
                Math.clamp(c.getInt(p + "blocks-per-tick", 2), 1, 256),
                Math.clamp(c.getInt(p + "max-falling-displays", 24), 1, 256),
                Math.clamp(c.getInt(p + "layer-delay-ticks", 10), 0, 200),
                parse(KeepOut.class, c.getString(p + "keep-out", "hold"), KeepOut.HOLD, p + "keep-out", problems),
                Math.clamp(c.getInt(p + "fake-air-per-tick", 4096), 64, 65536),
                Math.clamp(c.getInt(p + "refresh-hidden-ticks", 40), 0, 1200),
                Math.clamp(c.getInt(p + "reassert-radius", 2), 0, 16));
        return new HouseSettings(Math.clamp(c.getInt("house.max-blocks", 40000), 1, 1_000_000), cinematic);
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
