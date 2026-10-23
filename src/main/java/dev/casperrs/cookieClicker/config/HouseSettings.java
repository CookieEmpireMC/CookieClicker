package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * Design limits, the first-visit build cinematic, chimney smoke, factory steam and the ambient oven hum.
 *
 * @param maxBlocks largest design box (x * y * z) {@code /bakery house place} accepts; also the largest house region
 *                  box the cinematic will hide
 */
public record HouseSettings(int maxBlocks, Cinematic cinematic, Chimney chimney, Steam steam, Ambient ambient) {

    public enum Trigger { ENTER_PLOT, VIEWPOINT }

    public enum KeepOut { HOLD, SLOW, NONE }

    /**
     * @param blocksPerTick      blocks started per tick (0.5 = one every other tick); each landing is ~12 particles +
     *                           3 sounds, tuned for the factory (~600-1200 blocks, about 17-30 s)
     * @param maxFallingDisplays falling block displays alive per player (at most 24, the effects engine's cap)
     * @param slowAmount         {@code keep-out: slow}: movement speed modifier (-1 = stand still, 0 = normal)
     * @param refreshHiddenTicks re-send fake air for still-hidden blocks this often (0 = off)
     * @param reassertRadius     after an interaction within this many blocks of the house, re-send fake air
     * @param hammerEvery        play {@code house_hammer} on every n-th landing (0 = never)
     * @param camera             watch the build in spectator mode from the plot's camera point (when it has one)
     */
    public record Cinematic(boolean enabled, Trigger trigger, boolean teleportToViewpoint, double blocksPerTick,
                            int maxFallingDisplays, int layerDelayTicks, KeepOut keepOut, double slowAmount,
                            int fakeAirPerTick, int refreshHiddenTicks, int reassertRadius, int hammerEvery,
                            double viewpointRadius, double confettiHeight, boolean camera) {
    }

    /** @param crackleTicks {@code house_chimney_crackle} period: the file's length at its fixed pitch (3 s) */
    public record Chimney(boolean smoke, int crackleTicks) {
    }

    /** @param every ticks between the start of one vent's loop and the next, so vents don't puff together */
    public record Steam(boolean enabled, int every) {
    }

    /**
     * @param every  {@code factory_ambient} period: the file's length at its fixed pitch (4 s)
     * @param radius only for players within this many blocks of the first steam point (or the house centre)
     */
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
