package dev.casperrs.cookieClicker.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * {@code config.yml farm.*}: the countryside teleports, the {@code /bakery farm setup} paste speed and the waterwheel.
 * Cooldown in seconds (0 = none); blocks per tick 100..100000; wheel turns per minute 0..10 (0 = still), shown while a
 * player is within {@code wheelViewRange} blocks (8..256).
 */
public record FarmSettings(int teleportCooldownSeconds, int pasteBlocksPerTick, double wheelRpm, int wheelViewRange) {

    private static final int DEFAULT_COOLDOWN_SECONDS = 5;
    private static final int DEFAULT_BLOCKS_PER_TICK = 5000;
    private static final int MIN_BLOCKS_PER_TICK = 100;
    private static final int MAX_BLOCKS_PER_TICK = 100_000;
    private static final double DEFAULT_WHEEL_RPM = 2;
    private static final double MAX_WHEEL_RPM = 10;
    private static final int DEFAULT_WHEEL_VIEW_RANGE = 64;
    private static final int MIN_WHEEL_VIEW_RANGE = 8;
    private static final int MAX_WHEEL_VIEW_RANGE = 256;

    public FarmSettings(int teleportCooldownSeconds, int pasteBlocksPerTick) {
        this(teleportCooldownSeconds, pasteBlocksPerTick, DEFAULT_WHEEL_RPM, DEFAULT_WHEEL_VIEW_RANGE);
    }

    public static FarmSettings from(ConfigurationSection c, Consumer<String> problems) {
        int cooldown = c.getInt("farm.teleport-cooldown-seconds", DEFAULT_COOLDOWN_SECONDS);
        if (cooldown < 0) {
            problems.accept("config.yml: farm.teleport-cooldown-seconds must be >= 0; using "
                    + DEFAULT_COOLDOWN_SECONDS);
            cooldown = DEFAULT_COOLDOWN_SECONDS;
        }
        int perTick = c.getInt("farm.paste.blocks-per-tick", DEFAULT_BLOCKS_PER_TICK);
        if (perTick < MIN_BLOCKS_PER_TICK || perTick > MAX_BLOCKS_PER_TICK) {
            problems.accept("config.yml: farm.paste.blocks-per-tick must be " + MIN_BLOCKS_PER_TICK + ".."
                    + MAX_BLOCKS_PER_TICK + "; using " + DEFAULT_BLOCKS_PER_TICK);
            perTick = DEFAULT_BLOCKS_PER_TICK;
        }
        double rpm = c.getDouble("farm.wheel.rpm", DEFAULT_WHEEL_RPM);
        if (!(rpm >= 0 && rpm <= MAX_WHEEL_RPM)) {
            problems.accept("config.yml: farm.wheel.rpm must be 0.." + MAX_WHEEL_RPM + "; using " + DEFAULT_WHEEL_RPM);
            rpm = DEFAULT_WHEEL_RPM;
        }
        int range = c.getInt("farm.wheel.view-range", DEFAULT_WHEEL_VIEW_RANGE);
        if (range < MIN_WHEEL_VIEW_RANGE || range > MAX_WHEEL_VIEW_RANGE) {
            problems.accept("config.yml: farm.wheel.view-range must be " + MIN_WHEEL_VIEW_RANGE + ".."
                    + MAX_WHEEL_VIEW_RANGE + "; using " + DEFAULT_WHEEL_VIEW_RANGE);
            range = DEFAULT_WHEEL_VIEW_RANGE;
        }
        return new FarmSettings(cooldown, perTick, rpm, range);
    }
}
