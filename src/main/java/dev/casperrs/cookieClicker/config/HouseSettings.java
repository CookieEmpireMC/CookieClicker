package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * Design limits, the first-visit build cinematic, chimney smoke, factory steam and the ambient oven hum. maxBlocks is a
 * box volume (x * y * z), for {@code /bakery house place} and the house region the cinematic hides.
 */
public record HouseSettings(int maxBlocks, Cinematic cinematic, Chimney chimney, Steam steam, Ambient ambient) {

    public enum Trigger { ENTER_PLOT, VIEWPOINT }

    public enum KeepOut { HOLD, SLOW, NONE }

    /**
     * blocksPerTick may be fractional (0.5 = every other tick); maxFallingDisplays at most 24 (effects engine cap);
     * slowAmount -1 = stand still, 0 = normal; refreshHiddenTicks and hammerEvery 0 = off; reassertRadius in blocks.
     */
    public record Cinematic(boolean enabled, Trigger trigger, boolean teleportToViewpoint, double blocksPerTick,
                            int maxFallingDisplays, int layerDelayTicks, KeepOut keepOut, double slowAmount,
                            int fakeAirPerTick, int refreshHiddenTicks, int reassertRadius, int hammerEvery,
                            double viewpointRadius, double confettiHeight, boolean camera) {
    }

    /** crackleTicks: the crackle file's length at its fixed pitch (3 s). */
    public record Chimney(boolean smoke, int crackleTicks) {
    }

    /** {@code every}: ticks between two vents' loop starts, so vents don't puff together. */
    public record Steam(boolean enabled, int every) {
    }

    /** {@code every} in ticks (the hum file's length, 4 s); radius in blocks from the first steam point. */
    public record Ambient(boolean enabled, int every, double radius) {
    }

    private static final int CRACKLE_TICKS = 60;
    private static final int STEAM_EVERY = 7;
    private static final int AMBIENT_EVERY = 80;

    public static HouseSettings from(ConfigurationSection c, Consumer<String> problems) {
        Cinematic cinematic = new Cinematic(
                c.getBoolean("house.cinematic.enabled", true),
                Trigger.ENTER_PLOT, true, 2.25, 24, 5, KeepOut.HOLD, -0.7, 4096, 40, 2, 3, 2.0, 1.0,
                c.getBoolean("house.cinematic.camera", true));
        return new HouseSettings(
                Math.clamp(c.getInt("house.max-blocks", 40000), 1, 1_000_000),
                cinematic,
                new Chimney(c.getBoolean("house.chimney.smoke", true), CRACKLE_TICKS),
                new Steam(c.getBoolean("house.steam.enabled", true), STEAM_EVERY),
                new Ambient(c.getBoolean("house.ambient.enabled", true), AMBIENT_EVERY,
                        Math.clamp(c.getDouble("house.ambient.radius", 24.0), 1.0, 128.0)));
    }
}
